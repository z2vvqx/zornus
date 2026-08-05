package net.valoury.guilds.proxy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.*;
import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;
import net.valoury.shared.database.PostgresSchemaVerifier;
import net.valoury.shared.model.GroupJoinPolicy;
import net.valoury.shared.model.PlayerRecord;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.postgresql.util.PSQLException;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class GuildPostgresStorage implements GuildStorage, AutoCloseable {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    public GuildPostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(GuildProxyConstants.DATABASE_CONNECTION_POOL_SIZE);
        config.setDriverClassName("org.postgresql.Driver");
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
                "guilds-database-",
                GuildProxyConstants.DATABASE_EXECUTOR_POOL_SIZE
        );
        try {
            initializeSchema();
        } catch (RuntimeException exception) {
            databaseExecutor.shutdown();
            try {
                databaseExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            databaseExecutor.shutdownNow();
            dataSource.close();
            throw exception;
        }
    }

    private static boolean isUniqueConstraintViolation(
            SQLException exception,
            String expectedConstraintName
    ) {
        if (!"23505".equals(exception.getSQLState())
                || !(exception instanceof PSQLException postgresException)
                || postgresException.getServerErrorMessage() == null) {
            return false;
        }
        return expectedConstraintName.equals(
                postgresException.getServerErrorMessage().getConstraint());
    }

    private static Optional<CreateGuildOutcome> findGuildIdentityConflict(
            Connection connection,
            String guildName,
            String guildTag
    ) throws SQLException {
        String sql = """
                SELECT
                    EXISTS (
                        SELECT 1 FROM guilds WHERE LOWER(guild_name) = LOWER(?)
                    ) AS name_exists,
                    EXISTS (
                        SELECT 1 FROM guilds WHERE LOWER(guild_tag) = LOWER(?)
                    ) AS tag_exists
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildName);
            statement.setString(2, guildTag);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getBoolean("name_exists")) {
                    return Optional.of(new CreateGuildOutcome.GuildNameAlreadyExists());
                }
                if (resultSet.getBoolean("tag_exists")) {
                    return Optional.of(new CreateGuildOutcome.GuildTagAlreadyExists());
                }
                return Optional.empty();
            }
        }
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(GuildProxyConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                if (PostgresSchemaVerifier.relationExists(connection, "guilds")) {
                    validateSchema(connection);
                    connection.commit();
                    return;
                }
                // STEP 1: Create guild_players table
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_players (
                        player_id UUID PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        last_joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
                // UUID is the stable identity. Usernames can change and later be reused by a
                // different account, so keep last-known names non-unique and resolve them by
                // the most recent join.
                statement.execute("CREATE INDEX IF NOT EXISTS idx_guild_players_username_lower ON guild_players (LOWER(username))");

                // STEP 2: Create guild_members WITHOUT FK to guilds (avoids circular dependency)
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_members (
                        guild_id UUID NOT NULL,
                        player_id UUID NOT NULL UNIQUE,
                        guild_rank VARCHAR(16) NOT NULL,
                        CONSTRAINT chk_guild_members_rank CHECK (
                            guild_rank IN ('Leader', 'Director', 'Officer', 'Associate', 'Outcast')
                        ),
                        PRIMARY KEY (guild_id, player_id)
                    )
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_guild_members_guild ON guild_members(guild_id)");

                // STEP 3: Create guilds with deferred FK to guild_members
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS guilds (
                        guild_id UUID PRIMARY KEY,
                        guild_name VARCHAR(24) NOT NULL,
                        guild_tag VARCHAR(5) NOT NULL,
                        guild_color VARCHAR(32) NOT NULL DEFAULT '<white>',
                        leader_id UUID NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        CONSTRAINT fk_leader_is_member FOREIGN KEY (guild_id, leader_id)
                            REFERENCES guild_members(guild_id, player_id) DEFERRABLE INITIALLY DEFERRED
                    )
                    """);
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_guilds_name_ci ON guilds(LOWER(guild_name))");
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_guilds_tag_ci ON guilds(LOWER(guild_tag))");

                // STEP 4: Add FK from guild_members to guilds (now that both tables exist)
                if (!PostgresSchemaVerifier.constraintExists(
                        connection, "guild_members", "fk_guild_members_guild")) {
                    statement.execute("""
                        ALTER TABLE guild_members
                            ADD CONSTRAINT fk_guild_members_guild
                            FOREIGN KEY (guild_id) REFERENCES guilds(guild_id) ON DELETE CASCADE
                        """);
                }

                // STEP 5: Create guild_invitations
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_invitations (
                        guild_id UUID NOT NULL REFERENCES guilds(guild_id) ON DELETE CASCADE,
                        sender_id UUID NOT NULL,
                        target_id UUID NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (guild_id, sender_id, target_id)
                    )
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_guild_invitations_target ON guild_invitations(target_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_guild_invitations_sender ON guild_invitations(sender_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_guild_invitations_created ON guild_invitations(created_at DESC)");

                // STEP 6: Create guild_settings
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_settings (
                        player_id UUID PRIMARY KEY,
                        invite_privacy VARCHAR(8) NOT NULL DEFAULT 'all' CHECK (invite_privacy IN ('all', 'friend', 'none')),
                        show_chat BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """);

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_group_settings (
                        guild_id UUID PRIMARY KEY REFERENCES guilds(guild_id) ON DELETE CASCADE,
                        join_policy VARCHAR(8) NOT NULL DEFAULT 'private'
                            CHECK (join_policy IN ('private', 'public'))
                    )
                    """);

                // STEP 7: Create guild_cooldowns
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_cooldowns (
                        sender_id UUID NOT NULL,
                        receiver_id UUID NOT NULL,
                        timestamp TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (sender_id, receiver_id)
                    )
                    """);
                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_guild_cooldowns_timestamp
                    ON guild_cooldowns(timestamp)
                    """);

                // STEP 8: Create guild_confirmations
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_confirmations (
                        player_id UUID PRIMARY KEY,
                        confirmation_type VARCHAR(32) NOT NULL,
                        target_id UUID,
                        new_value VARCHAR(64),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_guild_confirmations_created
                    ON guild_confirmations(created_at)
                    """);

                validateSchema(connection);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize database schema", exception);
        }
    }

    private static void validateSchema(Connection connection) throws SQLException {
        PostgresSchemaVerifier.requireRelations(
                connection,
                "guild_players",
                "idx_guild_players_username_lower",
                "guild_members",
                "idx_guild_members_guild",
                "guilds",
                "idx_guilds_name_ci",
                "idx_guilds_tag_ci",
                "guild_invitations",
                "idx_guild_invitations_target",
                "idx_guild_invitations_sender",
                "idx_guild_invitations_created",
                "guild_settings",
                "guild_group_settings",
                "guild_cooldowns",
                "idx_guild_cooldowns_timestamp",
                "guild_confirmations",
                "idx_guild_confirmations_created"
        );
        PostgresSchemaVerifier.requireColumns(
                connection, "guild_players", "player_id", "username", "last_joined_at");
        PostgresSchemaVerifier.requireColumns(
                connection, "guild_members", "guild_id", "player_id", "guild_rank");
        PostgresSchemaVerifier.requireColumns(
                connection,
                "guilds",
                "guild_id",
                "guild_name",
                "guild_tag",
                "guild_color",
                "leader_id",
                "created_at"
        );
        PostgresSchemaVerifier.requireColumns(
                connection, "guild_invitations", "guild_id", "sender_id", "target_id", "created_at");
        PostgresSchemaVerifier.requireColumns(
                connection, "guild_settings", "player_id", "invite_privacy", "show_chat");
        PostgresSchemaVerifier.requireColumns(
                connection, "guild_group_settings", "guild_id", "join_policy");
        PostgresSchemaVerifier.requireColumns(
                connection, "guild_cooldowns", "sender_id", "receiver_id", "timestamp");
        PostgresSchemaVerifier.requireColumns(
                connection,
                "guild_confirmations",
                "player_id",
                "confirmation_type",
                "target_id",
                "new_value",
                "created_at"
        );
        PostgresSchemaVerifier.requireConstraints(
                connection, "guild_members", "chk_guild_members_rank", "fk_guild_members_guild");
        PostgresSchemaVerifier.requireConstraints(
                connection, "guilds", "fk_leader_is_member");
    }

    private <T> T executeQuery(String sql, SQLParameterSetter parameterSetter, ResultSetMapper<T> resultMapper, String operationName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameterSetter != null) {
                parameterSetter.setParameters(statement);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultMapper.map(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to " + operationName, exception);
        }
    }

    private int executeUpdate(String sql, SQLParameterSetter parameterSetter, String operationName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameterSetter != null) {
                parameterSetter.setParameters(statement);
            }
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to " + operationName, exception);
        }
    }

    @Override
    public CompletableFuture<CreateGuildOutcome> tryCreateGuild(@NonNull UUID leaderId, @NonNull String guildName, @NonNull String guildTag, @NonNull String guildColor) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Optional<CreateGuildOutcome> identityConflict =
                            findGuildIdentityConflict(connection, guildName, guildTag);
                    if (identityConflict.isPresent()) {
                        connection.rollback();
                        return identityConflict.get();
                    }

                    try (Statement deferStatement = connection.createStatement()) {
                        deferStatement.execute("SET CONSTRAINTS fk_leader_is_member DEFERRED");
                    }

                    UUID guildId = UUID.randomUUID();
                    Instant now = Instant.now();

                    // 1. Insert guild first (deferred fk_leader_is_member allows leader to not exist yet)
                    String insertGuildSql = "INSERT INTO guilds (guild_id, guild_name, guild_tag, guild_color, leader_id, created_at) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertGuildSql)) {
                        statement.setObject(1, guildId);
                        statement.setString(2, guildName);
                        statement.setString(3, guildTag);
                        statement.setString(4, guildColor);
                        statement.setObject(5, leaderId);
                        statement.setTimestamp(6, Timestamp.from(now));
                        statement.executeUpdate();
                    }

                    // 2. Insert leader into guild_members
                    String insertMemberSql =
                            "INSERT INTO guild_members (guild_id, player_id, guild_rank) VALUES (?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertMemberSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, leaderId);
                        statement.setString(3, GuildRank.LEADER.displayName());
                        statement.executeUpdate();
                    }

                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO guild_group_settings (guild_id) VALUES (?)")) {
                        statement.setObject(1, guildId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new CreateGuildOutcome.Created();

                } catch (SQLException exception) {
                    connection.rollback();
                    if (isUniqueConstraintViolation(exception, "idx_guilds_name_ci")) {
                        return new CreateGuildOutcome.GuildNameAlreadyExists();
                    }
                    if (isUniqueConstraintViolation(exception, "idx_guilds_tag_ci")) {
                        return new CreateGuildOutcome.GuildTagAlreadyExists();
                    }
                    if ("23505".equals(exception.getSQLState())) {
                        return new CreateGuildOutcome.AlreadyInGuild();
                    }
                    throw new RuntimeException("Failed to create guild", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to create guild", exception);
            }
        });
    }

    @Override
    public CompletableFuture<DisbandGuildOutcome> tryDisbandGuild(@NonNull UUID guildId, @NonNull UUID leaderId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement deferFkStatement = connection.prepareStatement(
                            "SET CONSTRAINTS fk_leader_is_member DEFERRED")) {
                        deferFkStatement.execute();
                    }

                    // 1. Lock guild_members first
                    String lockMembersSql = """
                            SELECT player_id
                            FROM guild_members
                            WHERE guild_id = ?
                            ORDER BY player_id
                            FOR UPDATE
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(lockMembersSql)) {
                        statement.setObject(1, guildId);
                        statement.executeQuery();
                    }

                    // 2. Verify leader
                    String checkLeaderSql = "SELECT leader_id FROM guilds WHERE guild_id = ?";
                    UUID currentLeaderId;
                    try (PreparedStatement statement = connection.prepareStatement(checkLeaderSql)) {
                        statement.setObject(1, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new DisbandGuildOutcome.GuildNotFound();
                            }
                            currentLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    if (!currentLeaderId.equals(leaderId)) {
                        connection.rollback();
                        return new DisbandGuildOutcome.NotLeader();
                    }

                    // 3. Delete confirmations for guild members
                    String deleteConfirmationsSql = """
                            DELETE FROM guild_confirmations
                            WHERE player_id IN (SELECT player_id FROM guild_members WHERE guild_id = ?)
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationsSql)) {
                        statement.setObject(1, guildId);
                        statement.executeUpdate();
                    }

                    // 4. Delete invitations
                    String deleteInvitationsSql = "DELETE FROM guild_invitations WHERE guild_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteInvitationsSql)) {
                        statement.setObject(1, guildId);
                        statement.executeUpdate();
                    }

                    // 5. Delete members
                    String deleteMembersSql = "DELETE FROM guild_members WHERE guild_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteMembersSql)) {
                        statement.setObject(1, guildId);
                        statement.executeUpdate();
                    }

                    // 6. Delete guild with conditional leader check
                    String deleteGuildSql = "DELETE FROM guilds WHERE guild_id = ? AND leader_id = ?";
                    int rowsDeleted;
                    try (PreparedStatement statement = connection.prepareStatement(deleteGuildSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, leaderId);
                        rowsDeleted = statement.executeUpdate();
                    }

                    if (rowsDeleted == 0) {
                        connection.rollback();
                        return new DisbandGuildOutcome.NotLeader();
                    }

                    connection.commit();
                    return new DisbandGuildOutcome.Disbanded();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw new RuntimeException("Failed to disband guild", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to disband guild", exception);
            }
        });
    }

    @Override
    public CompletableFuture<RemoveMemberOutcome> tryRemoveMember(@NonNull UUID guildId, @NonNull UUID memberId, @NonNull UUID requesterId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Lock guild_members rows first
                    String lockSql = """
                            SELECT player_id
                            FROM guild_members
                            WHERE guild_id = ?
                            ORDER BY player_id
                            FOR UPDATE
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(lockSql)) {
                        statement.setObject(1, guildId);
                        statement.executeQuery();
                    }

                    // 2. Get member count after locking
                    int memberCount;
                    String countSql = "SELECT COUNT(*) FROM guild_members WHERE guild_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                        statement.setObject(1, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            memberCount = resultSet.getInt(1);
                        }
                    }

                    // 3. Read leader_id AFTER the lock
                    UUID currentLeaderId;
                    String leaderSql = "SELECT leader_id FROM guilds WHERE guild_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(leaderSql)) {
                        statement.setObject(1, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new RemoveMemberOutcome.GuildNotFound();
                            }
                            currentLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    GuildRank requesterRank = null;
                    GuildRank targetRank = null;
                    String ranksSql = """
                            SELECT player_id, guild_rank
                            FROM guild_members
                            WHERE guild_id = ? AND player_id IN (?, ?)
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(ranksSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, requesterId);
                        statement.setObject(3, memberId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                UUID playerId = resultSet.getObject("player_id", UUID.class);
                                GuildRank rank = GuildRank.fromStoredName(
                                        resultSet.getString("guild_rank"));
                                if (playerId.equals(requesterId)) {
                                    requesterRank = rank;
                                }
                                if (playerId.equals(memberId)) {
                                    targetRank = rank;
                                }
                            }
                        }
                    }

                    if (targetRank == null) {
                        connection.rollback();
                        return new RemoveMemberOutcome.MemberNotFound();
                    }

                    boolean requesterLeaving = requesterId.equals(memberId);
                    boolean targetIsLeader =
                            memberId.equals(currentLeaderId) || targetRank == GuildRank.LEADER;

                    if (targetIsLeader) {
                        connection.rollback();
                        return requesterLeaving
                                ? new RemoveMemberOutcome.CannotRemoveLeader()
                                : new RemoveMemberOutcome.InsufficientRank();
                    }

                    if (!requesterLeaving
                            && (requesterRank == null || !requesterRank.canKick(targetRank))) {
                        connection.rollback();
                        return new RemoveMemberOutcome.InsufficientRank();
                    }

                    // 4. Delete the member
                    String deleteMemberSql = "DELETE FROM guild_members WHERE guild_id = ? AND player_id = ?";
                    int rowsDeleted;
                    try (PreparedStatement statement = connection.prepareStatement(deleteMemberSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, memberId);
                        rowsDeleted = statement.executeUpdate();
                    }

                    if (rowsDeleted == 0) {
                        connection.rollback();
                        return new RemoveMemberOutcome.MemberNotFound();
                    }

                    deleteOutgoingInvitations(connection, guildId, memberId);

                    // 5. Clean up any pending confirmation for the removed member
                    String deleteConfirmationSql = "DELETE FROM guild_confirmations WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationSql)) {
                        statement.setObject(1, memberId);
                        statement.executeUpdate();
                    }

                    // 6. If count was 1: delete guild
                    if (memberCount == 1) {
                        String deleteGuildSql = "DELETE FROM guilds WHERE guild_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(deleteGuildSql)) {
                            statement.setObject(1, guildId);
                            statement.executeUpdate();
                        }
                        connection.commit();
                        return new RemoveMemberOutcome.GuildDisbanded();
                    }

                    connection.commit();
                    return new RemoveMemberOutcome.MemberRemoved();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw new RuntimeException("Failed to remove member", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to remove member", exception);
            }
        });
    }

    @Override
    public CompletableFuture<SendInvitationOutcome> trySendInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId, boolean isFriend) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Verify and lock the sender membership before the guild row.
                    String checkSenderRankSql = """
                            SELECT guild_rank
                            FROM guild_members
                            WHERE guild_id = ? AND player_id = ?
                            FOR UPDATE
                            """;
                    GuildRank senderRank = null;
                    try (PreparedStatement statement = connection.prepareStatement(checkSenderRankSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, senderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                senderRank = GuildRank.fromStoredName(resultSet.getString("guild_rank"));
                            }
                        }
                    }

                    if (senderRank == null) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "SELECT 1 FROM guilds WHERE guild_id = ?")) {
                            statement.setObject(1, guildId);
                            try (ResultSet resultSet = statement.executeQuery()) {
                                boolean guildExists = resultSet.next();
                                connection.rollback();
                                return guildExists
                                        ? new SendInvitationOutcome.SenderInsufficientRank()
                                        : new SendInvitationOutcome.GuildNoLongerExists();
                            }
                        }
                    }
                    if (!senderRank.canManageInvitations()) {
                        connection.rollback();
                        return new SendInvitationOutcome.SenderInsufficientRank();
                    }

                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT 1 FROM guilds WHERE guild_id = ? FOR UPDATE")) {
                        statement.setObject(1, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new SendInvitationOutcome.GuildNoLongerExists();
                            }
                        }
                    }

                    // 2. Reject targets that cannot be added before policy and limit checks.
                    String checkTargetInGuildSql = "SELECT guild_id FROM guild_members WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkTargetInGuildSql)) {
                        statement.setObject(1, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                UUID targetGuildId = resultSet.getObject("guild_id", UUID.class);
                                connection.rollback();
                                return targetGuildId.equals(guildId)
                                        ? new SendInvitationOutcome.TargetAlreadyInGuild()
                                        : new SendInvitationOutcome.TargetInAnotherGuild();
                            }
                        }
                    }

                    String checkGuildSizeSql = "SELECT COUNT(*) FROM guild_members WHERE guild_id = ?";
                    int memberCount;
                    try (PreparedStatement statement = connection.prepareStatement(checkGuildSizeSql)) {
                        statement.setObject(1, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            memberCount = resultSet.getInt(1);
                        }
                    }
                    if (memberCount >= GuildProxyConstants.MAX_GUILD_SIZE) {
                        connection.rollback();
                        return new SendInvitationOutcome.GuildFull();
                    }

                    String checkExistingInviteSql =
                            "SELECT 1 FROM guild_invitations WHERE guild_id = ? AND target_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkExistingInviteSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new SendInvitationOutcome.AlreadyInvited();
                            }
                        }
                    }

                    // 3. Check target privacy settings.
                    String targetPrivacy = "all";
                    String checkPrivacySql = "SELECT invite_privacy FROM guild_settings WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkPrivacySql)) {
                        statement.setObject(1, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                targetPrivacy = resultSet.getString("invite_privacy");
                            }
                        }
                    }

                    switch (targetPrivacy) {
                        case "none" -> {
                            connection.rollback();
                            return new SendInvitationOutcome.InvitesDisabled("none");
                        }
                        case "friend" -> {
                            if (!isFriend) {
                                connection.rollback();
                                return new SendInvitationOutcome.InvitesDisabled("friend");
                            }
                        }
                        case "all" -> {
                            // Allowed - proceed
                        }
                        default -> {
                            connection.rollback();
                            return new SendInvitationOutcome.InvitesDisabled(targetPrivacy);
                        }
                    }

                    // 4. Check invitation cooldown.
                    String checkCooldownSql = "SELECT timestamp FROM guild_cooldowns WHERE sender_id = ? AND receiver_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkCooldownSql)) {
                        statement.setObject(1, senderId);
                        statement.setObject(2, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Timestamp lastTimestamp = resultSet.getTimestamp("timestamp");
                                Instant nextAllowed = lastTimestamp.toInstant().plus(GuildProxyConstants.INVITATION_COOLDOWN);
                                if (Instant.now().isBefore(nextAllowed)) {
                                    connection.rollback();
                                    return new SendInvitationOutcome.CooldownActive();
                                }
                            }
                        }
                    }

                    // Serialize invitation limit checks per player
                    acquirePerPlayerLocks(connection, senderId, targetId);

                    // 5. Check sender invitation limits.
                    String countSenderInvitesSql = """
                            SELECT (SELECT COUNT(*) FROM guild_invitations WHERE sender_id = ?) +
                                   (SELECT COUNT(*) FROM guild_invitations WHERE target_id = ?)
                            """;
                    int senderTotal;
                    try (PreparedStatement statement = connection.prepareStatement(countSenderInvitesSql)) {
                        statement.setObject(1, senderId);
                        statement.setObject(2, senderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            senderTotal = resultSet.getInt(1);
                        }
                    }
                    if (senderTotal >= GuildProxyConstants.MAX_GUILD_INVITATIONS) {
                        connection.rollback();
                        return new SendInvitationOutcome.SenderLimitReached();
                    }

                    // 6. Check receiver invitation limits.
                    String countReceiverInvitesSql = """
                            SELECT (SELECT COUNT(*) FROM guild_invitations WHERE sender_id = ?) +
                                   (SELECT COUNT(*) FROM guild_invitations WHERE target_id = ?)
                            """;
                    int receiverTotal;
                    try (PreparedStatement statement = connection.prepareStatement(countReceiverInvitesSql)) {
                        statement.setObject(1, targetId);
                        statement.setObject(2, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            receiverTotal = resultSet.getInt(1);
                        }
                    }
                    if (receiverTotal >= GuildProxyConstants.MAX_GUILD_INVITATIONS) {
                        connection.rollback();
                        return new SendInvitationOutcome.ReceiverLimitReached();
                    }

                    // 7. Insert invitation.
                    String insertInviteSql = """
                            INSERT INTO guild_invitations (guild_id, sender_id, target_id, created_at)
                            VALUES (?, ?, ?, NOW())
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(insertInviteSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, senderId);
                        statement.setObject(3, targetId);
                        statement.executeUpdate();
                    }

                    // 8. Record or refresh the cooldown.
                    String upsertCooldownSql = """
                            INSERT INTO guild_cooldowns (sender_id, receiver_id, timestamp)
                            VALUES (?, ?, NOW())
                            ON CONFLICT (sender_id, receiver_id) DO UPDATE SET timestamp = EXCLUDED.timestamp
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(upsertCooldownSql)) {
                        statement.setObject(1, senderId);
                        statement.setObject(2, targetId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new SendInvitationOutcome.Sent();
                } catch (SQLException exception) {
                    connection.rollback();
                    if ("23505".equals(exception.getSQLState())) {
                        return new SendInvitationOutcome.AlreadyInvited();
                    }
                    throw new RuntimeException("Failed to send invitation", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to send invitation", exception);
            }
        });
    }

    @Override
    public CompletableFuture<RevokeInvitationOutcome> tryRevokeInvitation(
            @NonNull UUID guildId,
            @NonNull UUID requesterId,
            @NonNull UUID targetId
    ) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    WITH requester AS (
                        SELECT guild_rank
                        FROM guild_members
                        WHERE guild_id = ? AND player_id = ?
                        FOR UPDATE
                    ),
                    deleted_invitation AS (
                        DELETE FROM guild_invitations
                        WHERE guild_id = ?
                          AND target_id = ?
                          AND EXISTS (
                              SELECT 1
                              FROM requester
                              WHERE guild_rank IN ('Leader', 'Director', 'Officer', 'Associate')
                          )
                        RETURNING 1
                    )
                    SELECT
                        EXISTS (SELECT 1 FROM guilds WHERE guild_id = ?) AS guild_exists,
                        (SELECT guild_rank FROM requester) AS requester_rank,
                        EXISTS (SELECT 1 FROM deleted_invitation) AS invitation_deleted
                    """;
            return executeQuery(sql, statement -> {
                statement.setObject(1, guildId);
                statement.setObject(2, requesterId);
                statement.setObject(3, guildId);
                statement.setObject(4, targetId);
                statement.setObject(5, guildId);
            }, resultSet -> {
                resultSet.next();
                if (!resultSet.getBoolean("guild_exists")) {
                    return new RevokeInvitationOutcome.GuildNotFound();
                }
                String requesterRank = resultSet.getString("requester_rank");
                if (requesterRank == null
                        || !GuildRank.fromStoredName(requesterRank)
                        .canManageInvitations()) {
                    return new RevokeInvitationOutcome.InsufficientRank();
                }
                return resultSet.getBoolean("invitation_deleted")
                        ? new RevokeInvitationOutcome.Revoked()
                        : new RevokeInvitationOutcome.InvitationNotFound();
            }, "revoke guild invitation");
        });
    }

    @Override
    public CompletableFuture<AcceptInvitationOutcome> tryAcceptInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement lockStatement = connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock(hashtextextended(?, 1))")) {
                    lockStatement.setString(1, guildId.toString());
                    lockStatement.executeQuery();
                }

                try {
                    String checkSenderMembershipSql = """
                            SELECT 1
                            FROM guild_members
                            WHERE guild_id = ? AND player_id = ?
                            FOR UPDATE
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(
                            checkSenderMembershipSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, senderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                deleteOutgoingInvitations(connection, guildId, senderId);
                                connection.commit();
                                return new AcceptInvitationOutcome.InvitationNoLongerValid();
                            }
                        }
                    }

                    // 1. Check invitation exists and is valid
                    String checkInviteSql = "SELECT created_at FROM guild_invitations WHERE guild_id = ? AND sender_id = ? AND target_id = ?";
                    Timestamp invitationCreated;
                    try (PreparedStatement statement = connection.prepareStatement(checkInviteSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, senderId);
                        statement.setObject(3, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new AcceptInvitationOutcome.InvitationNoLongerValid();
                            }
                            invitationCreated = resultSet.getTimestamp("created_at");
                        }
                    }

                    // Check if invitation is expired
                    Instant expiryTime = invitationCreated.toInstant().plus(GuildProxyConstants.INVITATION_EXPIRY);
                    if (Instant.now().isAfter(expiryTime)) {
                        String deleteExpiredSql = "DELETE FROM guild_invitations WHERE guild_id = ? AND sender_id = ? AND target_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(deleteExpiredSql)) {
                            statement.setObject(1, guildId);
                            statement.setObject(2, senderId);
                            statement.setObject(3, targetId);
                            statement.executeUpdate();
                        }
                        connection.commit();
                        return new AcceptInvitationOutcome.InvitationExpired();
                    }

                    // 2. Get guild member count
                    int memberCount;
                    String countSql = "SELECT COUNT(*) FROM guild_members WHERE guild_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                        statement.setObject(1, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            memberCount = resultSet.getInt(1);
                        }
                    }

                    // 3. If count >= MAX_GUILD_SIZE: rollback
                    if (memberCount >= GuildProxyConstants.MAX_GUILD_SIZE) {
                        connection.rollback();
                        return new AcceptInvitationOutcome.GuildFull();
                    }

                    // 4. Insert player into guild_members
                    String insertMemberSql =
                            "INSERT INTO guild_members (guild_id, player_id, guild_rank) VALUES (?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertMemberSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, targetId);
                        statement.setString(3, GuildRank.OUTCAST.displayName());
                        statement.executeUpdate();
                    }

                    // 5. Delete the invitation
                    String deleteInvitationSql = "DELETE FROM guild_invitations WHERE guild_id = ? AND sender_id = ? AND target_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteInvitationSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, senderId);
                        statement.setObject(3, targetId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new AcceptInvitationOutcome.Accepted();
                } catch (SQLException exception) {
                    connection.rollback();
                    if ("23505".equals(exception.getSQLState())) {
                        return new AcceptInvitationOutcome.AlreadyInGuild();
                    }
                    throw new RuntimeException("Failed to accept invitation", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to accept invitation", exception);
            }
        });
    }

    @Override
    public CompletableFuture<JoinPublicGuildOutcome> tryJoinPublicGuild(
            @NonNull UUID guildId,
            @NonNull UUID playerId
    ) {
        return databaseExecutor.supply(() -> GuildGroupPostgresOperations.joinPublicGuild(
                dataSource,
                guildId,
                playerId
        ));
    }

    @Override
    public CompletableFuture<TransferLeadershipOutcome> tryTransferLeadership(@NonNull UUID guildId, @NonNull UUID newLeaderId, @NonNull UUID oldLeaderId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Lock both affected memberships in stable order.
                    String checkMembersSql = """
                            SELECT player_id
                            FROM guild_members
                            WHERE guild_id = ? AND player_id IN (?, ?)
                            ORDER BY player_id
                            FOR UPDATE
                            """;
                    Set<UUID> memberIds = new HashSet<>();
                    try (PreparedStatement statement = connection.prepareStatement(checkMembersSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, newLeaderId);
                        statement.setObject(3, oldLeaderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                memberIds.add(resultSet.getObject("player_id", UUID.class));
                            }
                        }
                    }

                    if (!memberIds.contains(newLeaderId)) {
                        String checkGuildSql = "SELECT 1 FROM guilds WHERE guild_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(checkGuildSql)) {
                            statement.setObject(1, guildId);
                            try (ResultSet resultSet = statement.executeQuery()) {
                                if (!resultSet.next()) {
                                    connection.rollback();
                                    return new TransferLeadershipOutcome.GuildNotFound();
                                }
                            }
                        }
                        connection.rollback();
                        return new TransferLeadershipOutcome.TargetNotMember();
                    }
                    if (!memberIds.contains(oldLeaderId)) {
                        connection.rollback();
                        return new TransferLeadershipOutcome.GuildNotFound();
                    }

                    // 2. Verify old leader and update leader_id
                    String updateLeaderSql = "UPDATE guilds SET leader_id = ? WHERE guild_id = ? AND leader_id = ?";
                    int rowsUpdated;
                    try (PreparedStatement statement = connection.prepareStatement(updateLeaderSql)) {
                        statement.setObject(1, newLeaderId);
                        statement.setObject(2, guildId);
                        statement.setObject(3, oldLeaderId);
                        rowsUpdated = statement.executeUpdate();
                    }

                    if (rowsUpdated == 0) {
                        connection.rollback();
                        return new TransferLeadershipOutcome.GuildNotFound();
                    }

                    // 3. Keep the membership ranks aligned with the leadership record.
                    String updateRanksSql = """
                            UPDATE guild_members
                            SET guild_rank = CASE
                                WHEN player_id = ? THEN ?
                                WHEN player_id = ? THEN ?
                                ELSE guild_rank
                            END
                            WHERE guild_id = ? AND player_id IN (?, ?)
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(updateRanksSql)) {
                        statement.setObject(1, newLeaderId);
                        statement.setString(2, GuildRank.LEADER.displayName());
                        statement.setObject(3, oldLeaderId);
                        statement.setString(4, GuildRank.DIRECTOR.displayName());
                        statement.setObject(5, guildId);
                        statement.setObject(6, newLeaderId);
                        statement.setObject(7, oldLeaderId);
                        if (statement.executeUpdate() != 2) {
                            connection.rollback();
                            return new TransferLeadershipOutcome.TargetNotMember();
                        }
                    }

                    // 4. Delete confirmation for old leader
                    String deleteConfirmationSql = "DELETE FROM guild_confirmations WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationSql)) {
                        statement.setObject(1, oldLeaderId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new TransferLeadershipOutcome.Transferred();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw new RuntimeException("Failed to transfer leadership", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to transfer leadership", exception);
            }
        });
    }

    @Override
    public CompletableFuture<GuildRankChangeOutcome> tryChangeMemberRank(
            @NonNull UUID guildId,
            @NonNull UUID actorId,
            @NonNull UUID targetId,
            @NonNull GuildRankChangeDirection direction
    ) {
        return databaseExecutor.supply(() ->
                GuildRankPostgresOperations.changeMemberRank(
                        dataSource,
                        guildId,
                        actorId,
                        targetId,
                        direction
                ));
    }

    @Override
    public CompletableFuture<RenameGuildOutcome> tryRenameGuild(@NonNull UUID guildId, @NonNull UUID leaderId, @NonNull String newName) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Verify leader with FOR UPDATE
                    String checkLeaderSql = "SELECT leader_id FROM guilds WHERE guild_id = ? FOR UPDATE";
                    UUID currentLeaderId;
                    try (PreparedStatement statement = connection.prepareStatement(checkLeaderSql)) {
                        statement.setObject(1, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new RenameGuildOutcome.GuildNotFound();
                            }
                            currentLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    if (!currentLeaderId.equals(leaderId)) {
                        connection.rollback();
                        return new RenameGuildOutcome.NotLeader();
                    }

                    // 2. Check if new name already exists
                    String checkNameSql = "SELECT 1 FROM guilds WHERE LOWER(guild_name) = LOWER(?) AND guild_id != ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkNameSql)) {
                        statement.setString(1, newName);
                        statement.setObject(2, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new RenameGuildOutcome.NameAlreadyExists();
                            }
                        }
                    }

                    // 3. Update guild name
                    String updateNameSql = "UPDATE guilds SET guild_name = ? WHERE guild_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(updateNameSql)) {
                        statement.setString(1, newName);
                        statement.setObject(2, guildId);
                        statement.executeUpdate();
                    }

                    // 4. Delete confirmation for leader
                    String deleteConfirmationSql = "DELETE FROM guild_confirmations WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationSql)) {
                        statement.setObject(1, leaderId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new RenameGuildOutcome.Renamed();
                } catch (SQLException exception) {
                    connection.rollback();
                    if (isUniqueConstraintViolation(exception, "idx_guilds_name_ci")) {
                        return new RenameGuildOutcome.NameAlreadyExists();
                    }
                    if ("23505".equals(exception.getSQLState())) {
                        throw new RuntimeException("Unexpected unique violation during rename", exception);
                    }
                    throw new RuntimeException("Failed to rename guild", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to rename guild", exception);
            }
        });
    }

    // Single-query operations

    @Override
    public CompletableFuture<Optional<Guild>> fetchGuild(@NonNull UUID guildId) {
        return databaseExecutor.supply(() -> fetchGuildSync(guildId));
    }

    private Optional<Guild> fetchGuildSync(@NonNull UUID guildId) {
        String sql = """
                SELECT g.guild_id, g.guild_name, g.guild_tag, g.guild_color, g.leader_id, g.created_at,
                       gm.player_id, gm.guild_rank
                FROM guilds g
                LEFT JOIN guild_members gm ON g.guild_id = gm.guild_id
                WHERE g.guild_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return buildGuildFromResultSet(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to fetch guild", exception);
        }
    }

    @Override
    public CompletableFuture<Optional<Guild>> fetchGuildByName(@NonNull String name) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT g.guild_id, g.guild_name, g.guild_tag, g.guild_color, g.leader_id, g.created_at,
                           gm.player_id, gm.guild_rank
                    FROM guilds g
                    LEFT JOIN guild_members gm ON g.guild_id = gm.guild_id
                    WHERE LOWER(g.guild_name) = LOWER(?)
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return buildGuildFromResultSet(resultSet);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch guild by name", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Guild>> getPlayerGuild(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> fetchGuildByPlayerSync(playerId));
    }

    private Optional<Guild> fetchGuildByPlayerSync(@NonNull UUID playerId) {
        String sql = """
                SELECT g.guild_id, g.guild_name, g.guild_tag, g.guild_color, g.leader_id, g.created_at,
                       gm.player_id, gm.guild_rank
                FROM guild_members gm_leader
                JOIN guilds g ON gm_leader.guild_id = g.guild_id
                LEFT JOIN guild_members gm ON g.guild_id = gm.guild_id
                WHERE gm_leader.player_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return buildGuildFromResultSet(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to fetch player guild", exception);
        }
    }

    @Override
    public CompletableFuture<Boolean> isInGuild(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT 1 FROM guild_members WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), ResultSet::next, "check player in guild");
        });
    }

    @Override
    public CompletableFuture<List<GuildInvitation>> fetchIncomingInvitations(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT guild_id, sender_id, target_id, created_at FROM guild_invitations WHERE target_id = ? ORDER BY created_at DESC";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                List<GuildInvitation> invitations = new ArrayList<>();
                while (resultSet.next()) {
                    invitations.add(mapResultSetToGuildInvitation(resultSet));
                }
                return invitations;
            }, "fetch incoming invitations");
        });
    }

    @Override
    public CompletableFuture<List<GuildInvitation>> fetchOutgoingInvitations(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT guild_id, sender_id, target_id, created_at FROM guild_invitations WHERE sender_id = ? ORDER BY created_at DESC";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                List<GuildInvitation> invitations = new ArrayList<>();
                while (resultSet.next()) {
                    invitations.add(mapResultSetToGuildInvitation(resultSet));
                }
                return invitations;
            }, "fetch outgoing invitations");
        });
    }

    @Override
    public CompletableFuture<Optional<GuildInvitation>> findInvitationByGuildName(@NonNull UUID inviteeId, @NonNull String guildName) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT gi.guild_id, gi.sender_id, gi.target_id, gi.created_at
                    FROM guild_invitations gi
                    JOIN guilds g ON gi.guild_id = g.guild_id
                    WHERE gi.target_id = ? AND LOWER(g.guild_name) = LOWER(?)
                    ORDER BY gi.created_at DESC LIMIT 1
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, inviteeId);
                statement.setString(2, guildName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(mapResultSetToGuildInvitation(resultSet));
                    }
                    return Optional.empty();
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to find invitation by guild name", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removePendingInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId) {
        return databaseExecutor.supply(() -> {
            String sql = "DELETE FROM guild_invitations WHERE guild_id = ? AND sender_id = ? AND target_id = ?";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, guildId);
                statement.setObject(2, senderId);
                statement.setObject(3, targetId);
            }, "remove pending invitation");
            return rows > 0;
        });
    }

    @Override
    public CompletableFuture<Optional<GuildSettings>> fetchSettings(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT player_id, invite_privacy, show_chat FROM guild_settings WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToGuildSettings(resultSet));
                }
                return Optional.empty();
            }, "fetch settings");
        });
    }

    @Override
    public CompletableFuture<Optional<GuildGroupSettings>> fetchGroupSettings(
            @NonNull UUID guildId
    ) {
        return databaseExecutor.supply(() ->
                GuildGroupPostgresOperations.fetchSettings(dataSource, guildId));
    }

    @Override
    public CompletableFuture<Map<UUID, GuildSettings>> fetchSettingsForMembers(@NonNull Collection<UUID> memberIds) {
        return databaseExecutor.supply(() -> {
            if (memberIds.isEmpty()) {
                return Map.of();
            }
            String sql = "SELECT player_id, invite_privacy, show_chat FROM guild_settings WHERE player_id = ANY(?)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                Array array = connection.createArrayOf("uuid", memberIds.toArray());
                try {
                    statement.setArray(1, array);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        Map<UUID, GuildSettings> settingsMap = new HashMap<>();
                        while (resultSet.next()) {
                            GuildSettings settings = mapResultSetToGuildSettings(resultSet);
                            settingsMap.put(settings.playerId(), settings);
                        }
                        return settingsMap;
                    }
                } finally {
                    array.free();
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch settings for members", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateInvitePrivacy(@NonNull UUID playerId, @NonNull String value) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO guild_settings (player_id, invite_privacy, show_chat)
                    VALUES (?, ?, TRUE)
                    ON CONFLICT (player_id) DO UPDATE SET invite_privacy = EXCLUDED.invite_privacy
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, value);
            }, "update invite privacy");
        });
    }

    @Override
    public CompletableFuture<Void> updateShowChat(@NonNull UUID playerId, boolean value) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO guild_settings (player_id, invite_privacy, show_chat)
                    VALUES (?, 'all', ?)
                    ON CONFLICT (player_id) DO UPDATE SET show_chat = EXCLUDED.show_chat
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update show chat");
        });
    }

    @Override
    public CompletableFuture<UpdateGuildJoinPolicyOutcome> updateJoinPolicy(
            @NonNull UUID guildId,
            @NonNull UUID requesterId,
            @NonNull GroupJoinPolicy joinPolicy
    ) {
        return databaseExecutor.supply(() -> GuildGroupPostgresOperations.updateJoinPolicy(
                dataSource,
                guildId,
                requesterId,
                joinPolicy
        ));
    }

    @Override
    public CompletableFuture<UpdateGuildTagOutcome> tryUpdateGuildTag(
            @NonNull UUID guildId,
            @NonNull UUID leaderId,
            @NonNull String guildTag
    ) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    String checkLeaderSql =
                            "SELECT leader_id FROM guilds WHERE guild_id = ? FOR UPDATE";
                    UUID currentLeaderId;
                    try (PreparedStatement statement = connection.prepareStatement(checkLeaderSql)) {
                        statement.setObject(1, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new UpdateGuildTagOutcome.GuildNotFound();
                            }
                            currentLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    if (!currentLeaderId.equals(leaderId)) {
                        connection.rollback();
                        return new UpdateGuildTagOutcome.NotLeader();
                    }

                    String checkTagSql = """
                            SELECT 1
                            FROM guilds
                            WHERE LOWER(guild_tag) = LOWER(?) AND guild_id != ?
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(checkTagSql)) {
                        statement.setString(1, guildTag);
                        statement.setObject(2, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new UpdateGuildTagOutcome.GuildTagAlreadyExists();
                            }
                        }
                    }

                    String updateTagSql =
                            "UPDATE guilds SET guild_tag = ? WHERE guild_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(updateTagSql)) {
                        statement.setString(1, guildTag);
                        statement.setObject(2, guildId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new UpdateGuildTagOutcome.Updated();
                } catch (SQLException exception) {
                    connection.rollback();
                    if (isUniqueConstraintViolation(exception, "idx_guilds_tag_ci")) {
                        return new UpdateGuildTagOutcome.GuildTagAlreadyExists();
                    }
                    throw new RuntimeException("Failed to update guild tag", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to update guild tag", exception);
            }
        });
    }

    @Override
    public CompletableFuture<UpdateGuildColorOutcome> updateGuildColor(
            @NonNull UUID guildId,
            @NonNull UUID requesterId,
            @NonNull String guildColor
    ) {
        return databaseExecutor.supply(() ->
                GuildRankPostgresOperations.updateGuildColor(
                        dataSource,
                        guildId,
                        requesterId,
                        guildColor
                ));
    }

    @Override
    public CompletableFuture<Void> upsertPlayer(@NonNull UUID playerId, @NonNull String username) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO guild_players (player_id, username, last_joined_at)
                    VALUES (?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET username = EXCLUDED.username, last_joined_at = NOW()
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, username);
            }, "upsert player");
        });
    }

    @Override
    public CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(@NonNull String username) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT player_id, username FROM guild_players
                    WHERE LOWER(username) = LOWER(?)
                    ORDER BY last_joined_at DESC
                    LIMIT 1
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(new PlayerRecord(
                                (UUID) resultSet.getObject("player_id"),
                                resultSet.getString("username")
                        ));
                    }
                    return Optional.empty();
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch player by username", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<PlayerRecord>> fetchGuildMemberByUsername(
            @NonNull UUID guildId,
            @NonNull String username
    ) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT gp.player_id, gp.username
                    FROM guild_members gm
                    JOIN guild_players gp ON gp.player_id = gm.player_id
                    WHERE gm.guild_id = ? AND LOWER(gp.username) = LOWER(?)
                    ORDER BY gp.last_joined_at DESC, gp.player_id
                    LIMIT 1
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, guildId);
                statement.setString(2, username);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(new PlayerRecord(
                                resultSet.getObject("player_id", UUID.class),
                                resultSet.getString("username")
                        ));
                    }
                    return Optional.empty();
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch guild member by username", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Map<UUID, PlayerRecord>> fetchPlayersByUuids(@NonNull Collection<UUID> playerIds) {
        return databaseExecutor.supply(() -> {
            if (playerIds.isEmpty()) {
                return Map.of();
            }
            String sql = "SELECT player_id, username FROM guild_players WHERE player_id = ANY(?)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                Array array = connection.createArrayOf("uuid", playerIds.toArray());
                try {
                    statement.setArray(1, array);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        Map<UUID, PlayerRecord> playersMap = new HashMap<>();
                        while (resultSet.next()) {
                            UUID playerId = (UUID) resultSet.getObject("player_id");
                            String username = resultSet.getString("username");
                            playersMap.put(playerId, new PlayerRecord(playerId, username));
                        }
                        return playersMap;
                    }
                } finally {
                    array.free();
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to fetch players by UUIDs", exception);
            }
        });
    }

    @Override
    public CompletableFuture<ConfirmationOutcome> setPendingConfirmation(@NonNull PendingConfirmation confirmation) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    String selectSql = "SELECT confirmation_type, target_id, new_value, created_at FROM guild_confirmations WHERE player_id = ?";
                    Optional<PendingConfirmation> existingOptional;
                    try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                        statement.setObject(1, confirmation.playerId());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                String typeStr = resultSet.getString("confirmation_type");
                                ConfirmationType type = ConfirmationType.valueOf(typeStr);
                                UUID targetId = (UUID) resultSet.getObject("target_id");
                                String newValue = resultSet.getString("new_value");
                                Instant timestamp = resultSet.getTimestamp("created_at").toInstant();
                                existingOptional = Optional.of(new PendingConfirmation(confirmation.playerId(), type, targetId, newValue, timestamp));
                            } else {
                                existingOptional = Optional.empty();
                            }
                        }
                    }

                    if (existingOptional.isPresent()) {
                        connection.rollback();
                        return new ConfirmationOutcome.AlreadyExists(existingOptional.get());
                    }

                    String insertSql = "INSERT INTO guild_confirmations (player_id, confirmation_type, target_id, new_value, created_at) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                        statement.setObject(1, confirmation.playerId());
                        statement.setString(2, confirmation.type().name());
                        statement.setObject(3, confirmation.targetId());
                        statement.setString(4, confirmation.newValue());
                        statement.setTimestamp(5, Timestamp.from(confirmation.timestamp()));
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new ConfirmationOutcome.Set();
                } catch (SQLException exception) {
                    connection.rollback();
                    if ("23505".equals(exception.getSQLState())) {
                        String selectSql = "SELECT confirmation_type, target_id, new_value, created_at FROM guild_confirmations WHERE player_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                            statement.setObject(1, confirmation.playerId());
                            try (ResultSet resultSet = statement.executeQuery()) {
                                if (resultSet.next()) {
                                    String typeStr = resultSet.getString("confirmation_type");
                                    ConfirmationType type = ConfirmationType.valueOf(typeStr);
                                    UUID targetId = (UUID) resultSet.getObject("target_id");
                                    String newValue = resultSet.getString("new_value");
                                    Instant timestamp = resultSet.getTimestamp("created_at").toInstant();
                                    PendingConfirmation existing = new PendingConfirmation(confirmation.playerId(), type, targetId, newValue, timestamp);
                                    return new ConfirmationOutcome.AlreadyExists(existing);
                                }
                            }
                        } catch (SQLException fetchException) {
                            throw new RuntimeException("Failed to fetch existing confirmation after conflict", fetchException);
                        }
                    }
                    throw new RuntimeException("Failed to set pending confirmation", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to set pending confirmation", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Void> removePendingConfirmation(@NonNull UUID playerId) {
        return databaseExecutor.run(() -> {
            String sql = "DELETE FROM guild_confirmations WHERE player_id = ?";
            executeUpdate(sql, statement -> statement.setObject(1, playerId), "remove pending confirmation");
        });
    }

    @Override
    public CompletableFuture<Optional<PendingConfirmation>> fetchPendingConfirmation(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT player_id, confirmation_type, target_id, new_value, created_at FROM guild_confirmations WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPendingConfirmation(resultSet));
                }
                return Optional.empty();
            }, "fetch pending confirmation");
        });
    }

    @Override
    public CompletableFuture<Boolean> recordInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId, @NonNull Instant now) {
        return databaseExecutor.supply(() -> {
            String sql = "INSERT INTO guild_cooldowns (sender_id, receiver_id, timestamp) VALUES (?, ?, ?) ON CONFLICT (sender_id, receiver_id) DO UPDATE SET timestamp = EXCLUDED.timestamp";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, senderId);
                statement.setObject(2, receiverId);
                statement.setTimestamp(3, Timestamp.from(now));
            }, "record invitation cooldown");
            return rows > 0;
        });
    }

    @Override
    public CompletableFuture<Optional<Instant>> fetchInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT timestamp FROM guild_cooldowns WHERE sender_id = ? AND receiver_id = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, senderId);
                statement.setObject(2, receiverId);
            }, resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getTimestamp("timestamp").toInstant());
                }
                return Optional.empty();
            }, "fetch invitation cooldown");
        });
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredInvitations(@NonNull Instant now, @NonNull Duration expiry) {
        return databaseExecutor.run(() -> {
            String sql = """
                    DELETE FROM guild_invitations AS invitation
                    WHERE invitation.created_at < ?
                       OR NOT EXISTS (
                           SELECT 1
                           FROM guild_members AS member
                           WHERE member.guild_id = invitation.guild_id
                             AND member.player_id = invitation.sender_id
                       )
                    """;
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired invitations");
        });
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredConfirmations(@NonNull Instant now, @NonNull Duration expiry) {
        return databaseExecutor.run(() -> {
            String sql = "DELETE FROM guild_confirmations WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired confirmations");
        });
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredCooldowns(@NonNull Instant now, @NonNull Duration expiry) {
        return databaseExecutor.run(() -> {
            String sql = "DELETE FROM guild_cooldowns WHERE timestamp < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired cooldowns");
        });
    }

    // Helper methods

    private Optional<Guild> buildGuildFromResultSet(ResultSet resultSet) throws SQLException {
        UUID guildId = (UUID) resultSet.getObject("guild_id");
        String guildName = resultSet.getString("guild_name");
        String guildTag = resultSet.getString("guild_tag");
        String guildColor = resultSet.getString("guild_color");
        UUID leaderId = (UUID) resultSet.getObject("leader_id");
        Timestamp createdTimestamp = resultSet.getTimestamp("created_at");
        Instant createdAt = createdTimestamp != null ? createdTimestamp.toInstant() : Instant.now();

        Map<UUID, GuildRank> memberRanks = new HashMap<>();
        do {
            UUID memberId = (UUID) resultSet.getObject("player_id");
            if (memberId != null) {
                memberRanks.put(memberId, GuildRank.fromStoredName(resultSet.getString("guild_rank")));
            }
        } while (resultSet.next());

        return Optional.of(new Guild(
                guildId,
                guildName,
                guildTag,
                guildColor,
                leaderId,
                createdAt,
                memberRanks
        ));
    }

    @Contract("_ -> new")
    private @NonNull GuildInvitation mapResultSetToGuildInvitation(@NonNull ResultSet resultSet) throws SQLException {
        return new GuildInvitation(
                (UUID) resultSet.getObject("guild_id"),
                (UUID) resultSet.getObject("sender_id"),
                (UUID) resultSet.getObject("target_id"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    @Contract("_ -> new")
    private @NonNull PendingConfirmation mapResultSetToPendingConfirmation(@NonNull ResultSet resultSet) throws SQLException {
        String typeStr = resultSet.getString("confirmation_type");
        ConfirmationType type = ConfirmationType.valueOf(typeStr);
        return new PendingConfirmation(
                (UUID) resultSet.getObject("player_id"),
                type,
                (UUID) resultSet.getObject("target_id"),
                resultSet.getString("new_value"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    @Contract("_ -> new")
    private @NonNull GuildSettings mapResultSetToGuildSettings(@NonNull ResultSet resultSet) throws SQLException {
        return new GuildSettings(
                (UUID) resultSet.getObject("player_id"),
                resultSet.getString("invite_privacy"),
                resultSet.getBoolean("show_chat")
        );
    }

    private void acquirePerPlayerLocks(Connection connection, UUID player1, UUID player2) throws SQLException {
        UUID smaller = player1.compareTo(player2) < 0 ? player1 : player2;
        UUID larger = smaller.equals(player1) ? player2 : player1;
        try (PreparedStatement lockStatement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0)), pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            lockStatement.setString(1, smaller.toString());
            lockStatement.setString(2, larger.toString());
            lockStatement.executeQuery();
        }
    }

    private static void deleteOutgoingInvitations(
            @NonNull Connection connection,
            @NonNull UUID guildId,
            @NonNull UUID memberId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM guild_invitations
                WHERE guild_id = ? AND sender_id = ?
                """)) {
            statement.setObject(1, guildId);
            statement.setObject(2, memberId);
            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SQLParameterSetter {
        void setParameters(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
