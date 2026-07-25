package com.zornus.punishments.proxy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zornus.punishments.proxy.PunishmentProxyConstants;
import com.zornus.punishments.proxy.model.Punishment;
import com.zornus.punishments.proxy.model.PunishmentType;
import com.zornus.shared.database.DatabaseDefaults;
import com.zornus.shared.database.DatabaseExecutor;
import com.zornus.shared.model.PlayerRecord;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PunishmentPostgresStorage implements PunishmentStorage {
    private static final String RETURNING_COLUMNS = """
            RETURNING identifier, punishment_type, punished_player_id, imposing_player_id, reason,
                      created_at, expires_at, active, revoked_at, revoking_player_id,
                      revocation_reason, victim_notified, preset_name, preset_application_number
            """;

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    public PunishmentPostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl);
        configuration.setUsername(username);
        configuration.setPassword(password);
        configuration.setMaximumPoolSize(PunishmentProxyConstants.DATABASE_CONNECTION_POOL_SIZE);
        configuration.setDriverClassName("org.postgresql.Driver");
        configuration.setConnectionTimeout(DatabaseDefaults.CONNECTION_ACQUISITION_TIMEOUT_MILLISECONDS);
        configuration.setValidationTimeout(DatabaseDefaults.CONNECTION_VALIDATION_TIMEOUT_MILLISECONDS);
        configuration.addDataSourceProperty(
                "connectTimeout", DatabaseDefaults.CONNECTION_ESTABLISHMENT_TIMEOUT_SECONDS);
        configuration.addDataSourceProperty(
                "socketTimeout", DatabaseDefaults.SOCKET_READ_TIMEOUT_SECONDS);
        configuration.addDataSourceProperty(
                "cancelSignalTimeout", DatabaseDefaults.CANCEL_SIGNAL_TIMEOUT_SECONDS);
        configuration.addDataSourceProperty("options", DatabaseDefaults.POSTGRESQL_SESSION_OPTIONS);
        this.dataSource = new HikariDataSource(configuration);
        this.databaseExecutor = new DatabaseExecutor(
                "punishments-database-",
                PunishmentProxyConstants.DATABASE_EXECUTOR_POOL_SIZE
        );
        try {
            initializeSchema();
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (schemaExists(connection, "punishments")) {
                return;
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS punishment_players (
                        player_id UUID PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        last_joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_punishment_players_username_lower
                    ON punishment_players (LOWER(username))
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS punishments (
                        identifier VARCHAR(4) PRIMARY KEY,
                        punishment_type VARCHAR(8) NOT NULL
                            CHECK (punishment_type IN ('BAN', 'MUTE', 'WARN', 'KICK')),
                        punished_player_id UUID NOT NULL,
                        imposing_player_id UUID,
                        reason TEXT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL,
                        expires_at TIMESTAMPTZ,
                        active BOOLEAN NOT NULL,
                        revoked_at TIMESTAMPTZ,
                        revoking_player_id UUID,
                        revocation_reason TEXT,
                        victim_notified BOOLEAN NOT NULL DEFAULT FALSE,
                        preset_name VARCHAR(64),
                        preset_application_number INTEGER,
                        CONSTRAINT chk_punishments_preset_metadata CHECK (
                            (preset_name IS NULL AND preset_application_number IS NULL)
                            OR (
                                preset_name IS NOT NULL
                                AND preset_application_number IS NOT NULL
                                AND preset_application_number > 0
                            )
                        )
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_punishments_player_history
                    ON punishments (punished_player_id, created_at DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_punishments_active_expiry
                    ON punishments (expires_at)
                    WHERE active AND expires_at IS NOT NULL
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_punishments_one_active_type
                    ON punishments (punished_player_id, punishment_type)
                    WHERE active AND punishment_type IN ('BAN', 'MUTE')
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_punishments_one_active_warning_reason
                    ON punishments (punished_player_id, LOWER(BTRIM(reason)))
                    WHERE active AND punishment_type = 'WARN'
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_punishments_preset_application
                    ON punishments (punished_player_id, preset_name, preset_application_number)
                    WHERE preset_name IS NOT NULL AND preset_application_number IS NOT NULL
                    """);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize punishment database schema", exception);
        }
    }

    private static boolean schemaExists(Connection connection, String rootTable) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, rootTable);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    @Override
    public CompletableFuture<CreatePunishmentOutcome> createPunishment(@NonNull Punishment punishment) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    expirePunishmentsForPlayer(
                            connection, punishment.punishedPlayerId(), punishment.createdAt());
                    String insertSql = """
                            INSERT INTO punishments (
                                identifier, punishment_type, punished_player_id, imposing_player_id,
                                reason, created_at, expires_at, active, victim_notified,
                                preset_name, preset_application_number
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                        statement.setString(1, punishment.identifier().toUpperCase());
                        statement.setString(2, punishment.type().name());
                        statement.setObject(3, punishment.punishedPlayerId());
                        statement.setObject(4, punishment.imposingPlayerId());
                        statement.setString(5, punishment.reason());
                        statement.setTimestamp(6, Timestamp.from(punishment.createdAt()));
                        setInstant(statement, 7, punishment.expiresAt());
                        statement.setBoolean(8, punishment.active());
                        statement.setBoolean(9, punishment.victimNotified());
                        statement.setString(10, punishment.presetName());
                        if (punishment.presetApplicationNumber() == null) {
                            statement.setObject(11, null);
                        } else {
                            statement.setInt(11, punishment.presetApplicationNumber());
                        }
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return new CreatePunishmentOutcome.Created();
                } catch (SQLException exception) {
                    connection.rollback();
                    if ("23505".equals(exception.getSQLState())) {
                        if (identifierExists(connection, punishment.identifier())) {
                            return new CreatePunishmentOutcome.IdentifierCollision();
                        }
                        if (presetApplicationExists(connection, punishment)) {
                            return new CreatePunishmentOutcome.PresetProgressionConflict();
                        }
                        return new CreatePunishmentOutcome.AlreadyActive();
                    }
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to create punishment", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Punishment>> revokeByIdentifier(@NonNull String identifier, UUID revokerId,
                                                                       @NonNull String reason, @NonNull Instant revokedAt) {
        String sql = """
                UPDATE punishments
                SET active = FALSE, revoked_at = ?, revoking_player_id = ?, revocation_reason = ?
                WHERE identifier = ? AND active
                  AND (expires_at IS NULL OR expires_at > ?)
                """ + RETURNING_COLUMNS;
        return updateReturning(sql, statement -> {
            statement.setTimestamp(1, Timestamp.from(revokedAt));
            statement.setObject(2, revokerId);
            statement.setString(3, reason);
            statement.setString(4, identifier.toUpperCase());
            statement.setTimestamp(5, Timestamp.from(revokedAt));
        }, "revoke punishment by identifier");
    }

    @Override
    public CompletableFuture<Optional<Punishment>> revokeActive(@NonNull UUID playerId, @NonNull PunishmentType type,
                                                                 UUID revokerId, @NonNull String reason,
                                                                 @NonNull Instant revokedAt) {
        String sql = """
                UPDATE punishments
                SET active = FALSE, revoked_at = ?, revoking_player_id = ?, revocation_reason = ?
                WHERE identifier = (
                    SELECT identifier FROM punishments
                    WHERE punished_player_id = ? AND punishment_type = ? AND active
                      AND (expires_at IS NULL OR expires_at > ?)
                    ORDER BY created_at DESC LIMIT 1
                )
                """ + RETURNING_COLUMNS;
        return updateReturning(sql, statement -> {
            statement.setTimestamp(1, Timestamp.from(revokedAt));
            statement.setObject(2, revokerId);
            statement.setString(3, reason);
            statement.setObject(4, playerId);
            statement.setString(5, type.name());
            statement.setTimestamp(6, Timestamp.from(revokedAt));
        }, "revoke active punishment");
    }

    @Override
    public CompletableFuture<Optional<Punishment>> fetchByIdentifier(@NonNull String identifier) {
        String sql = """
                SELECT identifier, punishment_type, punished_player_id, imposing_player_id, reason,
                       created_at, expires_at,
                       active AND (expires_at IS NULL OR expires_at > NOW()) AS active,
                       revoked_at, revoking_player_id,
                       revocation_reason, victim_notified, preset_name, preset_application_number
                FROM punishments WHERE identifier = ?
                """;
        return queryOptional(sql, statement -> statement.setString(1, identifier.toUpperCase()),
                "fetch punishment by identifier");
    }

    @Override
    public CompletableFuture<Optional<Punishment>> fetchActive(@NonNull UUID playerId,
                                                                @NonNull PunishmentType type) {
        String sql = """
                SELECT identifier, punishment_type, punished_player_id, imposing_player_id, reason,
                       created_at, expires_at, active, revoked_at, revoking_player_id,
                       revocation_reason, victim_notified, preset_name, preset_application_number
                FROM punishments
                WHERE punished_player_id = ? AND punishment_type = ? AND active
                  AND (expires_at IS NULL OR expires_at > NOW())
                ORDER BY created_at DESC LIMIT 1
                """;
        return queryOptional(sql, statement -> {
            statement.setObject(1, playerId);
            statement.setString(2, type.name());
        }, "fetch active punishment");
    }

    @Override
    public CompletableFuture<List<Punishment>> fetchHistory(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT identifier, punishment_type, punished_player_id, imposing_player_id, reason,
                           created_at, expires_at,
                           active AND (expires_at IS NULL OR expires_at > NOW()) AS active,
                           revoked_at, revoking_player_id,
                           revocation_reason, victim_notified, preset_name, preset_application_number
                    FROM punishments WHERE punished_player_id = ? ORDER BY created_at DESC
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, playerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Punishment> punishments = new ArrayList<>();
                    while (resultSet.next()) {
                        punishments.add(mapPunishment(resultSet));
                    }
                    return List.copyOf(punishments);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch punishment history", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> fetchNextPresetApplicationNumber(
            @NonNull UUID playerId,
            @NonNull String presetName
    ) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT COALESCE(MAX(preset_application_number)::BIGINT, 0) + 1
                    FROM punishments
                    WHERE punished_player_id = ? AND preset_name = ?
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, playerId);
                statement.setString(2, presetName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    long applicationNumber = resultSet.getLong(1);
                    if (applicationNumber > Integer.MAX_VALUE) {
                        throw new IllegalStateException(
                                "Preset application count exceeds supported range for player " + playerId);
                    }
                    return Math.toIntExact(applicationNumber);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch next punishment preset application", exception);
            }
        });
    }

    @Override
    public CompletableFuture<List<Punishment>> claimPendingNotifications(@NonNull UUID playerId,
                                                                         @NonNull Instant now) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE punishments
                    SET victim_notified = TRUE
                    WHERE punished_player_id = ?
                      AND active
                      AND NOT victim_notified
                      AND punishment_type IN ('MUTE', 'WARN')
                      AND (expires_at IS NULL OR expires_at > ?)
                    """ + RETURNING_COLUMNS;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, playerId);
                statement.setTimestamp(2, Timestamp.from(now));
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Punishment> punishments = new ArrayList<>();
                    while (resultSet.next()) {
                        punishments.add(mapPunishment(resultSet));
                    }
                    return List.copyOf(punishments);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to claim pending punishment notifications", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> markNotificationDelivered(@NonNull String identifier) {
        return databaseExecutor.run(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE punishments SET victim_notified = TRUE WHERE identifier = ?")) {
                statement.setString(1, identifier.toUpperCase());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to mark punishment notification delivered", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> expirePunishments(@NonNull Instant now) {
        return databaseExecutor.run(() -> {
            try (Connection connection = dataSource.getConnection()) {
                expirePunishments(connection, now);
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to expire punishments", exception);
            }
        });
    }

    private void expirePunishments(Connection connection, Instant now) throws SQLException {
        String sql = """
                UPDATE punishments
                SET active = FALSE, revoked_at = ?, revocation_reason = ?
                WHERE active AND expires_at IS NOT NULL AND expires_at <= ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp timestamp = Timestamp.from(now);
            statement.setTimestamp(1, timestamp);
            statement.setString(2, PunishmentProxyConstants.EXPIRED_REASON);
            statement.setTimestamp(3, timestamp);
            statement.executeUpdate();
        }
    }

    private void expirePunishmentsForPlayer(
            Connection connection,
            UUID playerId,
            Instant now
    ) throws SQLException {
        String sql = """
                UPDATE punishments
                SET active = FALSE, revoked_at = ?, revocation_reason = ?
                WHERE punished_player_id = ?
                  AND active
                  AND expires_at IS NOT NULL
                  AND expires_at <= ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp timestamp = Timestamp.from(now);
            statement.setTimestamp(1, timestamp);
            statement.setString(2, PunishmentProxyConstants.EXPIRED_REASON);
            statement.setObject(3, playerId);
            statement.setTimestamp(4, timestamp);
            statement.executeUpdate();
        }
    }

    @Override
    public CompletableFuture<Void> upsertPlayer(@NonNull UUID playerId, @NonNull String username) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO punishment_players (player_id, username, last_joined_at)
                    VALUES (?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE
                    SET username = EXCLUDED.username, last_joined_at = EXCLUDED.last_joined_at
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, playerId);
                statement.setString(2, username);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to upsert punishment player", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<PlayerRecord>> fetchPlayer(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT player_id, username FROM punishment_players WHERE player_id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, playerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new PlayerRecord(
                            resultSet.getObject("player_id", UUID.class),
                            resultSet.getString("username")));
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch punishment player", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(@NonNull String username) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT player_id, username
                    FROM punishment_players
                    WHERE LOWER(username) = LOWER(?)
                    ORDER BY last_joined_at DESC
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
                            resultSet.getString("username")));
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch punishment player by username", exception);
            }
        });
    }

    private CompletableFuture<Optional<Punishment>> queryOptional(String sql, StatementSetter setter,
                                                                   String operationName) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                setter.set(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(mapPunishment(resultSet)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to " + operationName, exception);
            }
        });
    }

    private CompletableFuture<Optional<Punishment>> updateReturning(String sql, StatementSetter setter,
                                                                    String operationName) {
        return queryOptional(sql, setter, operationName);
    }

    private boolean identifierExists(Connection connection, String identifier) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                     "SELECT EXISTS (SELECT 1 FROM punishments WHERE identifier = ?)")) {
            statement.setString(1, identifier.toUpperCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private boolean presetApplicationExists(
            Connection connection,
            Punishment punishment
    ) throws SQLException {
        if (punishment.presetName() == null || punishment.presetApplicationNumber() == null) {
            return false;
        }
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM punishments
                    WHERE punished_player_id = ?
                      AND preset_name = ?
                      AND preset_application_number = ?
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, punishment.punishedPlayerId());
            statement.setString(2, punishment.presetName());
            statement.setInt(3, punishment.presetApplicationNumber());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private Punishment mapPunishment(ResultSet resultSet) throws SQLException {
        return new Punishment(
                resultSet.getString("identifier"),
                PunishmentType.valueOf(resultSet.getString("punishment_type")),
                resultSet.getObject("punished_player_id", UUID.class),
                resultSet.getObject("imposing_player_id", UUID.class),
                resultSet.getString("reason"),
                resultSet.getTimestamp("created_at").toInstant(),
                getInstant(resultSet, "expires_at"),
                resultSet.getBoolean("active"),
                getInstant(resultSet, "revoked_at"),
                resultSet.getObject("revoking_player_id", UUID.class),
                resultSet.getString("revocation_reason"),
                resultSet.getBoolean("victim_notified"),
                resultSet.getString("preset_name"),
                resultSet.getObject("preset_application_number", Integer.class)
        );
    }

    private static Instant getInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void setInstant(PreparedStatement statement, int index, Instant instant) throws SQLException {
        if (instant == null) {
            statement.setTimestamp(index, null);
        } else {
            statement.setTimestamp(index, Timestamp.from(instant));
        }
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(
                    PunishmentProxyConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }

    @FunctionalInterface
    private interface StatementSetter {
        void set(PreparedStatement statement) throws SQLException;
    }
}
