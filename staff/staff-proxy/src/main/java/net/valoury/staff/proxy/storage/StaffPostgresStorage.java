package net.valoury.staff.proxy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;
import net.valoury.shared.database.PostgresSchemaVerifier;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.staff.proxy.StaffProxyConstants;
import net.valoury.staff.proxy.model.AddressFingerprint;
import net.valoury.staff.proxy.model.ConnectionEdge;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class StaffPostgresStorage implements StaffStorage {
    private static final String OBSERVATIONS_TABLE = "staff_connection_observations";

    private final @NonNull HikariDataSource dataSource;
    private final @NonNull DatabaseExecutor databaseExecutor;

    public StaffPostgresStorage(
            @NonNull String jdbcUrl,
            @NonNull String username,
            @NonNull String password
    ) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl);
        configuration.setUsername(username);
        configuration.setPassword(password);
        configuration.setMaximumPoolSize(StaffProxyConstants.DATABASE_CONNECTION_POOL_SIZE);
        configuration.setDriverClassName("org.postgresql.Driver");
        configuration.setConnectionTimeout(
                DatabaseDefaults.CONNECTION_ACQUISITION_TIMEOUT_MILLISECONDS
        );
        configuration.setValidationTimeout(
                DatabaseDefaults.CONNECTION_VALIDATION_TIMEOUT_MILLISECONDS
        );
        configuration.addDataSourceProperty(
                "connectTimeout",
                DatabaseDefaults.CONNECTION_ESTABLISHMENT_TIMEOUT_SECONDS
        );
        configuration.addDataSourceProperty(
                "socketTimeout",
                DatabaseDefaults.SOCKET_READ_TIMEOUT_SECONDS
        );
        configuration.addDataSourceProperty(
                "cancelSignalTimeout",
                DatabaseDefaults.CANCEL_SIGNAL_TIMEOUT_SECONDS
        );
        configuration.addDataSourceProperty(
                "options",
                DatabaseDefaults.POSTGRESQL_SESSION_OPTIONS
        );
        this.dataSource = new HikariDataSource(configuration);
        this.databaseExecutor = new DatabaseExecutor(
                "staff-database-",
                StaffProxyConstants.DATABASE_EXECUTOR_POOL_SIZE
        );

        try {
            initializeSchema();
            cleanupExpiredConnectionsSynchronously();
        } catch (RuntimeException exception) {
            closeAfterInitializationFailure();
            throw exception;
        }
    }

    @Override
    public @NonNull CompletableFuture<Void> recordConnection(
            @NonNull UUID playerUuid,
            @NonNull String username,
            @NonNull AddressFingerprint addressFingerprint
    ) {
        return databaseExecutor.run(() -> {
            String insertSql = """
                    INSERT INTO staff_connection_observations (
                        player_id,
                        username,
                        address_fingerprint,
                        observed_at,
                        expires_at
                    )
                    VALUES (?, ?, ?, NOW(), NOW() + (? * INTERVAL '1 day'))
                    """;
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    deleteExpiredConnections(connection);
                    try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                        statement.setObject(1, playerUuid);
                        statement.setString(2, username);
                        statement.setString(3, addressFingerprint.encodedValue());
                        statement.setInt(4, StaffProxyConstants.CONNECTION_RETENTION_DAYS);
                        statement.executeUpdate();
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to record staff connection observation", exception);
            }
        });
    }

    @Override
    public @NonNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(
            @NonNull String username
    ) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT player_id, username
                    FROM staff_connection_observations
                    WHERE LOWER(username) = LOWER(?)
                      AND expires_at > NOW()
                    ORDER BY observed_at DESC
                    LIMIT 1
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new PlayerRecord(
                            resultSet.getObject("player_id", UUID.class),
                            resultSet.getString("username")
                    ));
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to resolve staff connection player", exception);
            }
        });
    }

    @Override
    public @NonNull CompletableFuture<List<ConnectionEdge>> fetchConnectedComponent(
            @NonNull UUID playerUuid
    ) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    WITH RECURSIVE connected_players(player_id) AS (
                        SELECT ?::UUID
                        UNION
                        SELECT candidate.player_id
                        FROM connected_players connected
                        JOIN staff_connection_observations source
                          ON source.player_id = connected.player_id
                         AND source.expires_at > NOW()
                        JOIN staff_connection_observations candidate
                          ON candidate.address_fingerprint = source.address_fingerprint
                         AND candidate.expires_at > NOW()
                    )
                    SELECT
                        observation.player_id,
                        (ARRAY_AGG(observation.username ORDER BY observation.observed_at DESC))[1]
                            AS username,
                        observation.address_fingerprint,
                        MIN(observation.observed_at) AS first_seen_at,
                        MAX(observation.observed_at) AS last_seen_at,
                        COUNT(*) AS connection_count
                    FROM staff_connection_observations observation
                    JOIN connected_players connected
                      ON connected.player_id = observation.player_id
                    WHERE observation.expires_at > NOW()
                    GROUP BY observation.player_id, observation.address_fingerprint
                    ORDER BY last_seen_at DESC, LOWER(
                        (ARRAY_AGG(observation.username ORDER BY observation.observed_at DESC))[1]
                    )
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, playerUuid);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ConnectionEdge> component = new ArrayList<>();
                    while (resultSet.next()) {
                        component.add(new ConnectionEdge(
                                resultSet.getObject("player_id", UUID.class),
                                resultSet.getString("username"),
                                new AddressFingerprint(
                                        resultSet.getString("address_fingerprint").trim()
                                ),
                                resultSet.getTimestamp("first_seen_at").toInstant(),
                                resultSet.getTimestamp("last_seen_at").toInstant(),
                                resultSet.getLong("connection_count")
                        ));
                    }
                    return List.copyOf(component);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch connected account component", exception);
            }
        });
    }

    @Override
    public @NonNull CompletableFuture<Void> cleanupExpiredConnections() {
        return databaseExecutor.run(this::cleanupExpiredConnectionsSynchronously);
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(
                    StaffProxyConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            )) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                if (!PostgresSchemaVerifier.relationExists(connection, OBSERVATIONS_TABLE)) {
                    statement.execute("""
                            CREATE TABLE staff_connection_observations (
                                observation_id BIGSERIAL PRIMARY KEY,
                                player_id UUID NOT NULL,
                                username VARCHAR(16) NOT NULL,
                                address_fingerprint CHAR(52) NOT NULL,
                                observed_at TIMESTAMPTZ NOT NULL,
                                expires_at TIMESTAMPTZ NOT NULL,
                                CONSTRAINT staff_connection_observations_valid_fingerprint
                                    CHECK (address_fingerprint ~ '^[0-9A-HJKMNP-TV-Z]{52}$'),
                                CONSTRAINT staff_connection_observations_valid_expiry
                                    CHECK (
                                        expires_at > observed_at
                                        AND expires_at <= observed_at + INTERVAL '%d days'
                                    )
                            )
                            """.formatted(
                                    StaffProxyConstants.CONNECTION_RETENTION_DAYS
                            ));
                    statement.execute("""
                            CREATE INDEX idx_staff_connections_player_expiry
                            ON staff_connection_observations (player_id, expires_at DESC)
                            """);
                    statement.execute("""
                            CREATE INDEX idx_staff_connections_address_expiry
                            ON staff_connection_observations (address_fingerprint, expires_at DESC)
                            """);
                    statement.execute("""
                            CREATE INDEX idx_staff_connections_username_expiry
                            ON staff_connection_observations (LOWER(username), expires_at DESC)
                            """);
                    statement.execute("""
                            CREATE INDEX idx_staff_connections_expiry
                            ON staff_connection_observations (expires_at)
                            """);
                }
                validateSchema(connection);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize staff database schema", exception);
        }
    }

    private static void validateSchema(Connection connection) throws SQLException {
        PostgresSchemaVerifier.requireRelations(
                connection,
                OBSERVATIONS_TABLE,
                "idx_staff_connections_player_expiry",
                "idx_staff_connections_address_expiry",
                "idx_staff_connections_username_expiry",
                "idx_staff_connections_expiry"
        );
        PostgresSchemaVerifier.requireColumns(
                connection,
                OBSERVATIONS_TABLE,
                "observation_id",
                "player_id",
                "username",
                "address_fingerprint",
                "observed_at",
                "expires_at"
        );
        PostgresSchemaVerifier.requireConstraints(
                connection,
                OBSERVATIONS_TABLE,
                "staff_connection_observations_pkey",
                "staff_connection_observations_valid_fingerprint",
                "staff_connection_observations_valid_expiry"
        );
    }

    private void cleanupExpiredConnectionsSynchronously() {
        try (Connection connection = dataSource.getConnection()) {
            deleteExpiredConnections(connection);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to clean expired staff connections", exception);
        }
    }

    private static void deleteExpiredConnections(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM staff_connection_observations WHERE expires_at <= NOW()"
        )) {
            statement.executeUpdate();
        }
    }

    private static void rollback(Connection connection, Throwable originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    private void closeAfterInitializationFailure() {
        databaseExecutor.shutdown();
        try {
            databaseExecutor.awaitTermination(
                    StaffProxyConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        databaseExecutor.shutdownNow();
        dataSource.close();
    }
}
