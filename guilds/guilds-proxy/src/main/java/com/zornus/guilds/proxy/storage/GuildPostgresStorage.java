package com.zornus.guilds.proxy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.model.*;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.postgresql.util.PSQLException;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class GuildPostgresStorage implements GuildStorage, AutoCloseable {

    private final HikariDataSource dataSource;
    private final ExecutorService databaseExecutor;

    public GuildPostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(GuildProxyConstants.DATABASE_CONNECTION_POOL_SIZE);
        config.setDriverClassName("org.postgresql.Driver");
        this.dataSource = new HikariDataSource(config);
        this.databaseExecutor = Executors.newFixedThreadPool(GuildProxyConstants.DATABASE_EXECUTOR_POOL_SIZE);
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
            // STEP 1: Create guild_players table
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_players (
                        player_id UUID PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        last_joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);

            // STEP 2: Create guild_members WITHOUT FK to guilds (avoids circular dependency)
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_members (
                        guild_id UUID NOT NULL,
                        player_id UUID NOT NULL UNIQUE,
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

            // STEP 4: Add FK from guild_members to guilds (now that both tables exist)
            try {
                statement.execute("""
                        ALTER TABLE guild_members
                            ADD CONSTRAINT fk_guild_members_guild
                            FOREIGN KEY (guild_id) REFERENCES guilds(guild_id) ON DELETE CASCADE
                        """);
            } catch (SQLException exception) {
                if (!"42710".equals(exception.getSQLState())) {
                    throw exception;
                }
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

            // STEP 7: Create guild_cooldowns
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_cooldowns (
                        sender_id UUID NOT NULL,
                        receiver_id UUID NOT NULL,
                        timestamp TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (sender_id, receiver_id)
                    )
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

        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize database schema", exception);
        }
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
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
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
                    String insertMemberSql = "INSERT INTO guild_members (guild_id, player_id) VALUES (?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertMemberSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, leaderId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new CreateGuildOutcome.Created();

                } catch (SQLException exception) {
                    connection.rollback();
                    if ("23505".equals(exception.getSQLState())) {
                        String constraintName = "";
                        if (exception instanceof PSQLException psqlException
                                && psqlException.getServerErrorMessage() != null) {
                            constraintName = Objects.toString(
                                    psqlException.getServerErrorMessage().getConstraint(), "");
                        }
                        if ("idx_guilds_name_ci".equals(constraintName)) {
                            return new CreateGuildOutcome.GuildNameAlreadyExists();
                        }
                        return new CreateGuildOutcome.AlreadyInGuild();
                    }
                    throw new RuntimeException("Failed to create guild", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to create guild", exception);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<DisbandGuildOutcome> tryDisbandGuild(@NonNull UUID guildId, @NonNull UUID leaderId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement deferFkStatement = connection.prepareStatement(
                            "SET CONSTRAINTS fk_leader_is_member DEFERRED")) {
                        deferFkStatement.execute();
                    }

                    // 1. Lock guild_members first
                    String lockMembersSql = "SELECT 1 FROM guild_members WHERE guild_id = ? FOR UPDATE";
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<RemoveMemberOutcome> tryRemoveMember(@NonNull UUID guildId, @NonNull UUID memberId, @NonNull UUID requesterId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Lock guild_members rows first
                    String lockSql = "SELECT 1 FROM guild_members WHERE guild_id = ? FOR UPDATE";
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

                    boolean isRequesterLeader = requesterId.equals(currentLeaderId);
                    boolean isTargetLeader = memberId.equals(currentLeaderId);

                    // If target is the leader, only they can transfer leadership (not kick themselves)
                    if (isTargetLeader) {
                        connection.rollback();
                        return isRequesterLeader 
                            ? new RemoveMemberOutcome.CannotRemoveLeader() 
                            : new RemoveMemberOutcome.NotLeader();
                    }

                    // If requester is not the leader, they can only remove themselves
                    if (!isRequesterLeader && !requesterId.equals(memberId)) {
                        connection.rollback();
                        return new RemoveMemberOutcome.NotLeader();
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<SendInvitationOutcome> trySendInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId, boolean isFriend) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try {
                    // 1. Verify sender is still leader of the guild
                    String checkLeaderSql = "SELECT leader_id FROM guilds WHERE guild_id = ?";
                    UUID actualLeaderId;
                    try (PreparedStatement statement = connection.prepareStatement(checkLeaderSql)) {
                        statement.setObject(1, guildId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new SendInvitationOutcome.GuildNoLongerExists();
                            }
                            actualLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    if (!actualLeaderId.equals(senderId)) {
                        connection.rollback();
                        return new SendInvitationOutcome.SenderNoLongerLeader();
                    }

                    // 2. Check target privacy settings
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

                    // 3. Check if target is already in a guild
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

                    // 4. Check if guild is full
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

                    // 5. Check invitation cooldown
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

                    // 6. Check sender invitation limits
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

                    // 7. Check receiver invitation limits
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

                    // 8. Check if already invited
                    String checkExistingInviteSql = "SELECT 1 FROM guild_invitations WHERE guild_id = ? AND sender_id = ? AND target_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkExistingInviteSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, senderId);
                        statement.setObject(3, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new SendInvitationOutcome.AlreadyInvited();
                            }
                        }
                    }

                    // 9. Insert invitation
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

                    // 10. Record/refresh cooldown
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
                    if ("23505".equals(exception.getSQLState()) || "40001".equals(exception.getSQLState())) {
                        return new SendInvitationOutcome.AlreadyInvited();
                    }
                    throw new RuntimeException("Failed to send invitation", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to send invitation", exception);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<AcceptInvitationOutcome> tryAcceptInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement lockStatement = connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock(hashtextextended(?, 1))")) {
                    lockStatement.setString(1, guildId.toString());
                    lockStatement.executeQuery();
                }

                try {
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
                    String insertMemberSql = "INSERT INTO guild_members (guild_id, player_id) VALUES (?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertMemberSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, targetId);
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<TransferLeadershipOutcome> tryTransferLeadership(@NonNull UUID guildId, @NonNull UUID newLeaderId, @NonNull UUID oldLeaderId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Verify target is still a guild member with FOR UPDATE
                    String checkMemberSql = "SELECT 1 FROM guild_members WHERE guild_id = ? AND player_id = ? FOR UPDATE";
                    boolean isMember;
                    try (PreparedStatement statement = connection.prepareStatement(checkMemberSql)) {
                        statement.setObject(1, guildId);
                        statement.setObject(2, newLeaderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            isMember = resultSet.next();
                        }
                    }

                    if (!isMember) {
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

                    // 3. Delete confirmation for old leader
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<RenameGuildOutcome> tryRenameGuild(@NonNull UUID guildId, @NonNull UUID leaderId, @NonNull String newName) {
        return CompletableFuture.supplyAsync(() -> {
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
                    if ("23505".equals(exception.getSQLState())) {
                        String constraintName = "";
                        if (exception instanceof PSQLException psqlException
                                && psqlException.getServerErrorMessage() != null) {
                            constraintName = Objects.toString(
                                    psqlException.getServerErrorMessage().getConstraint(), "");
                        }
                        if ("idx_guilds_name_ci".equals(constraintName)) {
                            return new RenameGuildOutcome.NameAlreadyExists();
                        }
                        throw new RuntimeException("Unexpected unique violation during rename", exception);
                    }
                    throw new RuntimeException("Failed to rename guild", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to rename guild", exception);
            }
        }, databaseExecutor);
    }

    // Single-query operations

    @Override
    public CompletableFuture<Optional<Guild>> fetchGuild(@NonNull UUID guildId) {
        return CompletableFuture.supplyAsync(() -> fetchGuildSync(guildId), databaseExecutor);
    }

    private Optional<Guild> fetchGuildSync(@NonNull UUID guildId) {
        String sql = """
                SELECT g.guild_id, g.guild_name, g.guild_tag, g.guild_color, g.leader_id, g.created_at,
                       gm.player_id
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
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    SELECT g.guild_id, g.guild_name, g.guild_tag, g.guild_color, g.leader_id, g.created_at,
                           gm.player_id
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<Guild>> getPlayerGuild(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> fetchGuildByPlayerSync(playerId), databaseExecutor);
    }

    private Optional<Guild> fetchGuildByPlayerSync(@NonNull UUID playerId) {
        String sql = """
                SELECT g.guild_id, g.guild_name, g.guild_tag, g.guild_color, g.leader_id, g.created_at,
                       gm.player_id
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
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM guild_members WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), ResultSet::next, "check player in guild");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<List<GuildInvitation>> fetchIncomingInvitations(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT guild_id, sender_id, target_id, created_at FROM guild_invitations WHERE target_id = ? ORDER BY created_at DESC";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                List<GuildInvitation> invitations = new ArrayList<>();
                while (resultSet.next()) {
                    invitations.add(mapResultSetToGuildInvitation(resultSet));
                }
                return invitations;
            }, "fetch incoming invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<List<GuildInvitation>> fetchOutgoingInvitations(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT guild_id, sender_id, target_id, created_at FROM guild_invitations WHERE sender_id = ? ORDER BY created_at DESC";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                List<GuildInvitation> invitations = new ArrayList<>();
                while (resultSet.next()) {
                    invitations.add(mapResultSetToGuildInvitation(resultSet));
                }
                return invitations;
            }, "fetch outgoing invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<GuildInvitation>> findInvitationByGuildName(@NonNull UUID inviteeId, @NonNull String guildName) {
        return CompletableFuture.supplyAsync(() -> {
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> removePendingInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM guild_invitations WHERE guild_id = ? AND sender_id = ? AND target_id = ?";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, guildId);
                statement.setObject(2, senderId);
                statement.setObject(3, targetId);
            }, "remove pending invitation");
            return rows > 0;
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<GuildSettings>> fetchSettings(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_id, invite_privacy, show_chat FROM guild_settings WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToGuildSettings(resultSet));
                }
                return Optional.empty();
            }, "fetch settings");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Map<UUID, GuildSettings>> fetchSettingsForMembers(@NonNull Collection<UUID> memberIds) {
        return CompletableFuture.supplyAsync(() -> {
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateInvitePrivacy(@NonNull UUID playerId, @NonNull String value) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO guild_settings (player_id, invite_privacy, show_chat)
                    VALUES (?, ?, TRUE)
                    ON CONFLICT (player_id) DO UPDATE SET invite_privacy = EXCLUDED.invite_privacy
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, value);
            }, "update invite privacy");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateShowChat(@NonNull UUID playerId, boolean value) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO guild_settings (player_id, invite_privacy, show_chat)
                    VALUES (?, 'all', ?)
                    ON CONFLICT (player_id) DO UPDATE SET show_chat = EXCLUDED.show_chat
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update show chat");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> upsertPlayer(@NonNull UUID playerId, @NonNull String username) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO guild_players (player_id, username, last_joined_at)
                    VALUES (?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET username = EXCLUDED.username, last_joined_at = NOW()
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, username);
            }, "upsert player");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(@NonNull String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_id, username FROM guild_players WHERE LOWER(username) = LOWER(?)";
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Map<UUID, PlayerRecord>> fetchPlayersByUuids(@NonNull Collection<UUID> playerIds) {
        return CompletableFuture.supplyAsync(() -> {
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<ConfirmationOutcome> setPendingConfirmation(@NonNull PendingConfirmation confirmation) {
        return CompletableFuture.supplyAsync(() -> {
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> removePendingConfirmation(@NonNull UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM guild_confirmations WHERE player_id = ?";
            executeUpdate(sql, statement -> statement.setObject(1, playerId), "remove pending confirmation");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<PendingConfirmation>> fetchPendingConfirmation(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_id, confirmation_type, target_id, new_value, created_at FROM guild_confirmations WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPendingConfirmation(resultSet));
                }
                return Optional.empty();
            }, "fetch pending confirmation");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> recordInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId, @NonNull Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO guild_cooldowns (sender_id, receiver_id, timestamp) VALUES (?, ?, ?) ON CONFLICT (sender_id, receiver_id) DO UPDATE SET timestamp = EXCLUDED.timestamp";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, senderId);
                statement.setObject(2, receiverId);
                statement.setTimestamp(3, Timestamp.from(now));
            }, "record invitation cooldown");
            return rows > 0;
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<Instant>> fetchInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId) {
        return CompletableFuture.supplyAsync(() -> {
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
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredInvitations(@NonNull Instant now, @NonNull Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM guild_invitations WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredConfirmations(@NonNull Instant now, @NonNull Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM guild_confirmations WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired confirmations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredCooldowns(@NonNull Instant now, @NonNull Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM guild_cooldowns WHERE timestamp < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired cooldowns");
        }, databaseExecutor);
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

        Set<UUID> memberIds = new HashSet<>();
        do {
            UUID memberId = (UUID) resultSet.getObject("player_id");
            if (memberId != null) {
                memberIds.add(memberId);
            }
        } while (resultSet.next());

        return Optional.of(new Guild(guildId, guildName, guildTag, guildColor, leaderId, createdAt, memberIds));
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

    @FunctionalInterface
    private interface SQLParameterSetter {
        void setParameters(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
