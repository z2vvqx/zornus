package net.valoury.guilds.server.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.valoury.guilds.api.GuildProfile;
import net.valoury.guilds.server.GuildsServerConstants;
import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class GuildServerPostgresStorage implements GuildServerStorage {

    private static final String FIND_GUILD_BY_PLAYER = """
            SELECT g.guild_id, g.guild_name, g.guild_tag, g.guild_color
            FROM guild_members AS gm
            INNER JOIN guilds AS g ON g.guild_id = gm.guild_id
            WHERE gm.player_id = ?
            """;
    private static final String FIND_GUILD = """
            SELECT guild_id, guild_name, guild_tag, guild_color
            FROM guilds
            WHERE guild_id = ?
            """;
    private static final Set<String> REQUIRED_GUILDS_COLUMNS =
            Set.of("guild_id", "guild_name", "guild_tag", "guild_color");
    private static final Set<String> REQUIRED_MEMBERSHIP_COLUMNS =
            Set.of("guild_id", "player_id");

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    public GuildServerPostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(GuildsServerConstants.DATABASE_CONNECTION_POOL_SIZE);
        config.setDriverClassName(org.postgresql.Driver.class.getName());
        config.setReadOnly(true);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(DatabaseDefaults.CONNECTION_ACQUISITION_TIMEOUT_MILLISECONDS);
        config.setValidationTimeout(DatabaseDefaults.CONNECTION_VALIDATION_TIMEOUT_MILLISECONDS);
        config.addDataSourceProperty(
                "connectTimeout", DatabaseDefaults.CONNECTION_ESTABLISHMENT_TIMEOUT_SECONDS);
        config.addDataSourceProperty("socketTimeout", DatabaseDefaults.SOCKET_READ_TIMEOUT_SECONDS);
        config.addDataSourceProperty(
                "cancelSignalTimeout", DatabaseDefaults.CANCEL_SIGNAL_TIMEOUT_SECONDS);
        config.addDataSourceProperty("options", DatabaseDefaults.POSTGRESQL_SESSION_OPTIONS);
        this.dataSource = new HikariDataSource(config);
        this.databaseExecutor = new DatabaseExecutor(
                "guilds-server-database-",
                GuildsServerConstants.DATABASE_EXECUTOR_POOL_SIZE
        );
    }

    @Override
    public @NonNull CompletableFuture<Void> validateSchema() {
        return databaseExecutor.run(() -> inReadOnlyTransaction(connection -> {
            validateTable(connection, "guilds", REQUIRED_GUILDS_COLUMNS);
            validateTable(connection, "guild_members", REQUIRED_MEMBERSHIP_COLUMNS);
            return null;
        }, "validate guild schema"));
    }

    @Override
    public @NonNull CompletableFuture<Optional<GuildProfile>> findGuildByPlayer(
            @NonNull UUID playerId
    ) {
        return databaseExecutor.supply(() -> queryGuild(
                FIND_GUILD_BY_PLAYER,
                playerId,
                "find guild by player"
        ));
    }

    @Override
    public @NonNull CompletableFuture<Optional<GuildProfile>> findGuild(@NonNull UUID guildId) {
        return databaseExecutor.supply(() -> queryGuild(FIND_GUILD, guildId, "find guild"));
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(
                    GuildsServerConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS,
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

    private Optional<GuildProfile> queryGuild(String sql, UUID id, String operation) {
        return inReadOnlyTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new GuildProfile(
                            resultSet.getObject("guild_id", UUID.class),
                            resultSet.getString("guild_name"),
                            resultSet.getString("guild_tag"),
                            resultSet.getString("guild_color")
                    ));
                }
            }
        }, operation);
    }

    private static void validateTable(
            Connection connection,
            String tableName,
            Set<String> requiredColumns
    ) throws SQLException {
        try (PreparedStatement tableStatement = connection.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL")) {
            tableStatement.setString(1, tableName);
            try (ResultSet resultSet = tableStatement.executeQuery()) {
                resultSet.next();
                if (!resultSet.getBoolean(1)) {
                    throw new IllegalStateException(
                            "Required Guilds Proxy table is missing: " + tableName);
                }
            }
        }

        try (PreparedStatement columnStatement = connection.prepareStatement("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ?
                """)) {
            columnStatement.setString(1, tableName);
            Set<String> missingColumns = new java.util.HashSet<>(requiredColumns);
            try (ResultSet resultSet = columnStatement.executeQuery()) {
                while (resultSet.next()) {
                    missingColumns.remove(resultSet.getString(1));
                }
            }
            if (!missingColumns.isEmpty()) {
                throw new IllegalStateException(
                        "Required columns are missing from " + tableName + ": " + missingColumns);
            }
        }
    }

    private <T> T inReadOnlyTransaction(
            SqlFunction<T> operation,
            String operationName
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                T result = operation.apply(connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to " + operationName, exception);
        }
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws Exception;
    }
}
