package com.zornus.parties.proxy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.*;
import com.zornus.parties.proxy.utilities.CooldownKey;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PartyPostgresStorage implements PartyStorage, AutoCloseable {

    private final HikariDataSource dataSource;
    private final ExecutorService databaseExecutor;

    public PartyPostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(PartyProxyConstants.DATABASE_CONNECTION_POOL_SIZE);
        config.setDriverClassName("org.postgresql.Driver");
        this.dataSource = new HikariDataSource(config);
        this.databaseExecutor = Executors.newFixedThreadPool(PartyProxyConstants.DATABASE_EXECUTOR_POOL_SIZE);
        try {
            initializeSchema();
        } catch (RuntimeException exception) {
            // Clean up resources if schema initialization fails
            dataSource.close();
            databaseExecutor.shutdown();
            try {
                databaseExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            databaseExecutor.shutdownNow();
            throw exception;
        }
    }

    @Override
    public void close() {
        dataSource.close();
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(PartyProxyConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            // STEP 1: Create party_members WITHOUT FK to parties (avoids circular dependency)
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_members (
                        party_id UUID NOT NULL,
                        player_id UUID NOT NULL UNIQUE,
                        PRIMARY KEY (party_id, player_id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_party_members_party ON party_members(party_id)");

            // STEP 2: Create parties with deferred FK to party_members
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS parties (
                        party_id UUID PRIMARY KEY,
                        leader_id UUID NOT NULL,
                        last_warp_time TIMESTAMPTZ,
                        CONSTRAINT fk_leader_is_member FOREIGN KEY (party_id, leader_id)
                            REFERENCES party_members(party_id, player_id) DEFERRABLE INITIALLY DEFERRED
                    )
                    """);

            // STEP 3: Add FK from party_members to parties (now that both tables exist)
            // Wrapped in exception handler to allow re-running initializeSchema safely
            try {
                statement.execute("""
                        ALTER TABLE party_members
                            ADD CONSTRAINT fk_party_members_party
                            FOREIGN KEY (party_id) REFERENCES parties(party_id) ON DELETE CASCADE
                        """);
            } catch (SQLException e) {
                // Constraint already exists - ignore
                if (!"42710".equals(e.getSQLState())) {
                    throw e;
                }
            }

            // Remaining tables (unchanged structure)
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_invitations (
                        party_id UUID NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
                        sender_id UUID NOT NULL,
                        target_id UUID NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (party_id, sender_id, target_id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_invitations_target ON party_invitations(target_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_invitations_sender ON party_invitations(sender_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_invitations_created ON party_invitations(created_at DESC)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_settings (
                        player_id UUID PRIMARY KEY,
                        allow_chat BOOLEAN NOT NULL DEFAULT TRUE,
                        allow_warp BOOLEAN NOT NULL DEFAULT TRUE,
                        invite_privacy VARCHAR(8) NOT NULL DEFAULT 'all' CHECK (invite_privacy IN ('all', 'friend', 'none'))
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_confirmations (
                        player_id UUID PRIMARY KEY,
                        confirmation_type VARCHAR(32) NOT NULL,
                        target_id UUID,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_cooldowns (
                        player_a UUID NOT NULL,
                        player_b UUID NOT NULL,
                        timestamp TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (player_a, player_b)
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

    // ==================== COMPOUND OPERATIONS ====================

    @Override
    public CompletableFuture<CreatePartyOutcome> createParty(@NonNull Party party) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // Defer the leader-is-member FK check until commit
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("SET CONSTRAINTS fk_leader_is_member DEFERRED");
                    }

                    Instant now = party.lastWarpTime().orElse(Instant.now());

                    // 1. Insert party FIRST (deferred fk_leader_is_member allows leader to not exist yet)
                    String insertPartySql = "INSERT INTO parties (party_id, leader_id, last_warp_time) VALUES (?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertPartySql)) {
                        statement.setObject(1, party.partyId());
                        statement.setObject(2, party.leaderId());
                        statement.setTimestamp(3, Timestamp.from(now));
                        statement.executeUpdate();
                    }

                    // 2. Insert leader into party_members (satisfies immediate fk_party_members_party)
                    // UNIQUE constraint on player_id catches AlreadyInParty here
                    String insertMemberSql = "INSERT INTO party_members (party_id, player_id) VALUES (?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertMemberSql)) {
                        statement.setObject(1, party.partyId());
                        statement.setObject(2, party.leaderId());
                        statement.executeUpdate();
                    }

                    // Both FKs validated here (deferred fk_leader_is_member checks leader is member)
                    connection.commit();
                    return new CreatePartyOutcome.Created();

                } catch (SQLException e) {
                    connection.rollback();
                    // Check for unique_violation on player_id (player already in party)
                    if ("23505".equals(e.getSQLState())) {
                        return new CreatePartyOutcome.AlreadyInParty();
                    }
                    throw new RuntimeException("Failed to create party", e);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create party", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<DisbandPartyOutcome> disbandParty(@NonNull UUID partyId, @NonNull UUID leaderId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Verify the requester is the leader
                    String checkLeaderSql = "SELECT leader_id FROM parties WHERE party_id = ?";
                    UUID actualLeaderId;
                    try (PreparedStatement statement = connection.prepareStatement(checkLeaderSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new DisbandPartyOutcome.PartyNotFound();
                            }
                            actualLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    if (!actualLeaderId.equals(leaderId)) {
                        connection.rollback();
                        return new DisbandPartyOutcome.NotLeader();
                    }

                    // 2. Delete all member confirmations (cascade through FK)
                    String deleteConfirmationsSql = """
                            DELETE FROM party_confirmations
                            WHERE player_id IN (SELECT player_id FROM party_members WHERE party_id = ?)
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationsSql)) {
                        statement.setObject(1, partyId);
                        statement.executeUpdate();
                    }

                    // 3. Delete party (party_members and party_invitations cascade via FK)
                    String deletePartySql = "DELETE FROM parties WHERE party_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deletePartySql)) {
                        statement.setObject(1, partyId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new DisbandPartyOutcome.Disbanded();
                } catch (SQLException e) {
                    connection.rollback();
                    throw new RuntimeException("Failed to disband party", e);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to disband party", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<RemoveMemberOutcome> removeMember(@NonNull UUID partyId, @NonNull UUID memberId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Check if party exists and get current leader
                    UUID currentLeaderId;
                    String checkPartySql = "SELECT leader_id FROM parties WHERE party_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkPartySql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new RemoveMemberOutcome.PartyNotFound();
                            }
                            currentLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    // 2. Get member count and verify member exists
                    int memberCount;
                    boolean wasLeader = memberId.equals(currentLeaderId);
                    String countSql = "SELECT COUNT(*) FROM party_members WHERE party_id = ? FOR UPDATE";
                    try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            memberCount = resultSet.getInt(1);
                        }
                    }

                    // 3. Delete the member
                    String deleteMemberSql = "DELETE FROM party_members WHERE party_id = ? AND player_id = ?";
                    int rowsDeleted;
                    try (PreparedStatement statement = connection.prepareStatement(deleteMemberSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, memberId);
                        rowsDeleted = statement.executeUpdate();
                    }

                    if (rowsDeleted == 0) {
                        connection.rollback();
                        return new RemoveMemberOutcome.MemberNotFound();
                    }

                    // 4. If count was 1: delete party → return PartyDisbanded
                    if (memberCount == 1) {
                        String deletePartySql = "DELETE FROM parties WHERE party_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(deletePartySql)) {
                            statement.setObject(1, partyId);
                            statement.executeUpdate();
                        }
                        connection.commit();
                        return new RemoveMemberOutcome.PartyDisbanded();
                    }

                    // 5. If leader left: select new leader (alphabetically first UUID among remaining)
                    if (wasLeader) {
                        String selectNewLeaderSql = """
                                SELECT player_id FROM party_members
                                WHERE party_id = ? AND player_id != ?
                                ORDER BY player_id ASC LIMIT 1
                                """;
                        UUID newLeaderId;
                        try (PreparedStatement statement = connection.prepareStatement(selectNewLeaderSql)) {
                            statement.setObject(1, partyId);
                            statement.setObject(2, memberId);
                            try (ResultSet resultSet = statement.executeQuery()) {
                                resultSet.next();
                                newLeaderId = (UUID) resultSet.getObject("player_id");
                            }
                        }

                        String updateLeaderSql = "UPDATE parties SET leader_id = ? WHERE party_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(updateLeaderSql)) {
                            statement.setObject(1, newLeaderId);
                            statement.setObject(2, partyId);
                            statement.executeUpdate();
                        }
                        connection.commit();
                        return new RemoveMemberOutcome.LeaderTransferred(newLeaderId);
                    }

                    connection.commit();
                    return new RemoveMemberOutcome.MemberRemoved();
                } catch (SQLException e) {
                    connection.rollback();
                    throw new RuntimeException("Failed to remove member", e);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove member", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<JoinOutcome> acceptInvitationAndJoin(@NonNull UUID partyId, @NonNull UUID playerId, @NonNull UUID invitationSenderId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try {
                    // 1. Check if player is already in a party (UNIQUE constraint on party_members.player_id)
                    // This is caught by the INSERT later, but we check early for better error messages

                    // 2. Check invitation exists and is valid
                    String checkInviteSql = "SELECT created_at FROM party_invitations WHERE party_id = ? AND sender_id = ? AND target_id = ?";
                    Timestamp invitationCreated;
                    try (PreparedStatement statement = connection.prepareStatement(checkInviteSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, invitationSenderId);
                        statement.setObject(3, playerId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new JoinOutcome.InvitationNoLongerValid();
                            }
                            invitationCreated = resultSet.getTimestamp("created_at");
                        }
                    }

                    // Check if invitation is expired
                    Instant expiryTime = invitationCreated.toInstant().plus(PartyProxyConstants.INVITATION_EXPIRY);
                    if (Instant.now().isAfter(expiryTime)) {
                        // Delete expired invitation and commit to persist cleanup
                        String deleteExpiredSql = "DELETE FROM party_invitations WHERE party_id = ? AND sender_id = ? AND target_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(deleteExpiredSql)) {
                            statement.setObject(1, partyId);
                            statement.setObject(2, invitationSenderId);
                            statement.setObject(3, playerId);
                            statement.executeUpdate();
                        }
                        connection.commit();
                        return new JoinOutcome.InvitationExpired();
                    }

                    // 3. Get party member count with FOR UPDATE
                    int memberCount;
                    String countSql = "SELECT COUNT(*) FROM party_members WHERE party_id = ? FOR UPDATE";
                    try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            memberCount = resultSet.getInt(1);
                        }
                    }

                    // 4. If count >= MAX_PARTY_SIZE: rollback, return PartyFull
                    if (memberCount >= PartyProxyConstants.MAX_PARTY_SIZE) {
                        connection.rollback();
                        return new JoinOutcome.PartyFull();
                    }

                    // 5. Verify sender is still the party leader
                    String checkLeaderSql = "SELECT leader_id FROM parties WHERE party_id = ?";
                    UUID currentLeaderId;
                    try (PreparedStatement statement = connection.prepareStatement(checkLeaderSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new JoinOutcome.InvitationNoLongerValid();
                            }
                            currentLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    if (!currentLeaderId.equals(invitationSenderId)) {
                        // Sender is no longer leader - invalidate invitation
                        String deleteInvalidSql = "DELETE FROM party_invitations WHERE party_id = ? AND sender_id = ? AND target_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(deleteInvalidSql)) {
                            statement.setObject(1, partyId);
                            statement.setObject(2, invitationSenderId);
                            statement.setObject(3, playerId);
                            statement.executeUpdate();
                        }
                        connection.rollback();
                        return new JoinOutcome.InvitationNoLongerValid();
                    }

                    // 6. Insert player into party_members - UNIQUE constraint catches AlreadyMember
                    String insertMemberSql = "INSERT INTO party_members (party_id, player_id) VALUES (?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertMemberSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, playerId);
                        statement.executeUpdate();
                    }

                    // 7. Delete the invitation
                    String deleteInvitationSql = "DELETE FROM party_invitations WHERE party_id = ? AND sender_id = ? AND target_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteInvitationSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, invitationSenderId);
                        statement.setObject(3, playerId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new JoinOutcome.Joined();
                } catch (SQLException e) {
                    connection.rollback();
                    // Check for unique_violation on player_id (player already in party)
                    if ("23505".equals(e.getSQLState())) {
                        return new JoinOutcome.AlreadyMember();
                    }
                    throw new RuntimeException("Failed to accept invitation and join", e);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to accept invitation and join", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<TransferLeadershipOutcome> transferLeadership(@NonNull UUID partyId, @NonNull UUID newLeaderId, @NonNull UUID confirmedByPlayerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Verify target is still a party member with FOR UPDATE
                    String checkMemberSql = "SELECT 1 FROM party_members WHERE party_id = ? AND player_id = ? FOR UPDATE";
                    boolean isMember;
                    try (PreparedStatement statement = connection.prepareStatement(checkMemberSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, newLeaderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            isMember = resultSet.next();
                        }
                    }

                    if (!isMember) {
                        // Check if party exists at all
                        String checkPartySql = "SELECT 1 FROM parties WHERE party_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(checkPartySql)) {
                            statement.setObject(1, partyId);
                            try (ResultSet resultSet = statement.executeQuery()) {
                                if (!resultSet.next()) {
                                    connection.rollback();
                                    return new TransferLeadershipOutcome.PartyNotFound();
                                }
                            }
                        }
                        connection.rollback();
                        return new TransferLeadershipOutcome.TargetNotMember();
                    }

                    // 2. UPDATE parties SET leader_id = ? WHERE party_id = ?
                    String updateLeaderSql = "UPDATE parties SET leader_id = ? WHERE party_id = ?";
                    int rowsUpdated;
                    try (PreparedStatement statement = connection.prepareStatement(updateLeaderSql)) {
                        statement.setObject(1, newLeaderId);
                        statement.setObject(2, partyId);
                        rowsUpdated = statement.executeUpdate();
                    }

                    if (rowsUpdated == 0) {
                        connection.rollback();
                        return new TransferLeadershipOutcome.PartyNotFound();
                    }

                    // 3. DELETE FROM party_confirmations WHERE player_id = ?
                    String deleteConfirmationSql = "DELETE FROM party_confirmations WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationSql)) {
                        statement.setObject(1, confirmedByPlayerId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new TransferLeadershipOutcome.Transferred();
                } catch (SQLException e) {
                    connection.rollback();
                    throw new RuntimeException("Failed to transfer leadership", e);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to transfer leadership", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<WarpOutcome> checkAndUpdateLastWarpTime(@NonNull UUID partyId, @NonNull Instant now,
                                                                      @NonNull Duration cooldown) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. SELECT last_warp_time FROM parties WHERE party_id = ? FOR UPDATE
                    Optional<Instant> lastWarpOptional;
                    String selectSql = "SELECT last_warp_time FROM parties WHERE party_id = ? FOR UPDATE";
                    try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Timestamp lastWarpTimestamp = resultSet.getTimestamp("last_warp_time");
                                lastWarpOptional = Optional.ofNullable(lastWarpTimestamp).map(Timestamp::toInstant);
                            } else {
                                connection.rollback();
                                return new WarpOutcome.PartyNotFound();
                            }
                        }
                    }

                    // 2. If cooldown not elapsed: rollback, return OnCooldown
                    if (lastWarpOptional.isPresent()) {
                        Instant cooldownEnd = lastWarpOptional.get().plus(cooldown);
                        if (now.isBefore(cooldownEnd)) {
                            connection.rollback();
                            return new WarpOutcome.OnCooldown();
                        }
                    }

                    // 3. UPDATE parties SET last_warp_time = ?
                    String updateSql = "UPDATE parties SET last_warp_time = ? WHERE party_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                        statement.setTimestamp(1, Timestamp.from(now));
                        statement.setObject(2, partyId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new WarpOutcome.Allowed();
                } catch (SQLException e) {
                    connection.rollback();
                    throw new RuntimeException("Failed to check and update warp time", e);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check and update warp time", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Map<UUID, PartySettings>> fetchSettingsForMembers(@NonNull Collection<UUID> memberIds) {
        return CompletableFuture.supplyAsync(() -> {
            if (memberIds.isEmpty()) {
                return Map.of();
            }

            String sql = "SELECT player_id, allow_chat, allow_warp, invite_privacy FROM party_settings WHERE player_id = ANY(?)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                Array array = connection.createArrayOf("uuid", memberIds.toArray());
                try {
                    statement.setArray(1, array);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        Map<UUID, PartySettings> settingsMap = new HashMap<>();
                        while (resultSet.next()) {
                            PartySettings settings = mapResultSetToPartySettings(resultSet);
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
    public CompletableFuture<SendInvitationOutcome> trySendInvitation(@NonNull UUID partyId, @NonNull UUID senderId, @NonNull UUID targetId, boolean isFriend) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try {
                    // 1. Verify sender is still leader of the party
                    String checkLeaderSql = "SELECT leader_id FROM parties WHERE party_id = ?";
                    UUID actualLeaderId;
                    try (PreparedStatement statement = connection.prepareStatement(checkLeaderSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new SendInvitationOutcome.PartyNoLongerExists();
                            }
                            actualLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    if (!actualLeaderId.equals(senderId)) {
                        connection.rollback();
                        return new SendInvitationOutcome.SenderNoLongerLeader();
                    }

                    // 2. Check target privacy settings
                    String targetPrivacy = "all"; // default
                    String checkPrivacySql = "SELECT invite_privacy FROM party_settings WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkPrivacySql)) {
                        statement.setObject(1, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                targetPrivacy = resultSet.getString("invite_privacy");
                            }
                        }
                    }

                    // Enforce privacy rules atomically - fail closed for any unexpected value
                    switch (targetPrivacy) {
                        case "none" -> {
                            connection.rollback();
                            return new SendInvitationOutcome.InvitesDisabled("none");
                        }
                        case "friend" -> {
                            // Verify friendship - fail closed if not a friend
                            if (!isFriend) {
                                connection.rollback();
                                return new SendInvitationOutcome.InvitesDisabled("friend");
                            }
                        }
                        case "all" -> {
                            // Allowed - proceed
                        }
                        default -> {
                            // Fail closed: reject for any unexpected privacy value
                            connection.rollback();
                            return new SendInvitationOutcome.InvitesDisabled(targetPrivacy);
                        }
                    }

                    // 3. Check if target is already in a party (via party_members UNIQUE constraint check)
                    String checkTargetInPartySql = "SELECT 1 FROM party_members WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkTargetInPartySql)) {
                        statement.setObject(1, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new SendInvitationOutcome.TargetAlreadyInParty();
                            }
                        }
                    }

                    // 4. Check if party is full
                    String checkPartySizeSql = "SELECT COUNT(*) FROM party_members WHERE party_id = ?";
                    int memberCount;
                    try (PreparedStatement statement = connection.prepareStatement(checkPartySizeSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            memberCount = resultSet.getInt(1);
                        }
                    }
                    if (memberCount >= PartyProxyConstants.MAX_PARTY_SIZE) {
                        connection.rollback();
                        return new SendInvitationOutcome.PartyFull();
                    }

                    // 5. Check invitation cooldown using canonicalized keys
                    CooldownKey.CanonicalKey cooldownKey = CooldownKey.canonicalize(senderId, targetId);
                    String checkCooldownSql = "SELECT timestamp FROM party_cooldowns WHERE player_a = ? AND player_b = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkCooldownSql)) {
                        statement.setObject(1, cooldownKey.smaller());
                        statement.setObject(2, cooldownKey.larger());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Timestamp lastTimestamp = resultSet.getTimestamp("timestamp");
                                Instant nextAllowed = lastTimestamp.toInstant().plus(PartyProxyConstants.INVITATION_COOLDOWN);
                                if (Instant.now().isBefore(nextAllowed)) {
                                    connection.rollback();
                                    return new SendInvitationOutcome.CooldownActive();
                                }
                            }
                        }
                    }

                    // 5. Check sender invitation limits
                    String countSenderInvitesSql = """
                        SELECT (SELECT COUNT(*) FROM party_invitations WHERE sender_id = ?) +
                               (SELECT COUNT(*) FROM party_invitations WHERE target_id = ?)
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
                    if (senderTotal >= PartyProxyConstants.MAX_PARTY_INVITATIONS) {
                        connection.rollback();
                        return new SendInvitationOutcome.SenderLimitReached();
                    }

                    // 6. Check receiver invitation limits
                    String countReceiverInvitesSql = """
                        SELECT (SELECT COUNT(*) FROM party_invitations WHERE sender_id = ?) +
                               (SELECT COUNT(*) FROM party_invitations WHERE target_id = ?)
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
                    if (receiverTotal >= PartyProxyConstants.MAX_PARTY_INVITATIONS) {
                        connection.rollback();
                        return new SendInvitationOutcome.ReceiverLimitReached();
                    }

                    // 7. Check if already invited
                    String checkExistingInviteSql = "SELECT 1 FROM party_invitations WHERE party_id = ? AND sender_id = ? AND target_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkExistingInviteSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, senderId);
                        statement.setObject(3, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new SendInvitationOutcome.AlreadyInvited();
                            }
                        }
                    }

                    // 8. Insert invitation (no name columns)
                    String insertInviteSql = """
                        INSERT INTO party_invitations (party_id, sender_id, target_id, created_at)
                        VALUES (?, ?, ?, NOW())
                        """;
                    try (PreparedStatement statement = connection.prepareStatement(insertInviteSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, senderId);
                        statement.setObject(3, targetId);
                        statement.executeUpdate();
                    }

                    // 9. Record/refresh cooldown using canonicalized keys
                    CooldownKey.CanonicalKey key = CooldownKey.canonicalize(senderId, targetId);
                    String upsertCooldownSql = """
                        INSERT INTO party_cooldowns (player_a, player_b, timestamp)
                        VALUES (?, ?, NOW())
                        ON CONFLICT (player_a, player_b) DO UPDATE SET timestamp = EXCLUDED.timestamp
                        """;
                    try (PreparedStatement statement = connection.prepareStatement(upsertCooldownSql)) {
                        statement.setObject(1, key.smaller());
                        statement.setObject(2, key.larger());
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new SendInvitationOutcome.Sent();
                } catch (SQLException e) {
                    connection.rollback();
                    throw new RuntimeException("Failed to send invitation", e);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to send invitation", e);
            }
        }, databaseExecutor);
    }

    // ==================== SINGLE-QUERY OPERATIONS ====================

    @Override
    public CompletableFuture<Optional<Party>> fetchParty(@NonNull UUID partyId) {
        return CompletableFuture.supplyAsync(() -> fetchPartySync(partyId), databaseExecutor);
    }

    private Optional<Party> fetchPartySync(@NonNull UUID partyId) {
        String sql = """
                SELECT p.party_id, p.leader_id, p.last_warp_time,
                       pm.player_id
                FROM parties p
                LEFT JOIN party_members pm ON p.party_id = pm.party_id
                WHERE p.party_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, partyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return buildPartyFromResultSet(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to fetch party", exception);
        }
    }

    private Optional<Party> buildPartyFromResultSet(ResultSet resultSet) throws SQLException {
        UUID partyId = (UUID) resultSet.getObject("party_id");
        UUID leaderId = (UUID) resultSet.getObject("leader_id");
        Timestamp lastWarpTimestamp = resultSet.getTimestamp("last_warp_time");
        Optional<Instant> lastWarpTime = Optional.ofNullable(lastWarpTimestamp).map(Timestamp::toInstant);

        Set<UUID> memberIds = new HashSet<>();
        do {
            UUID memberId = (UUID) resultSet.getObject("player_id");
            if (memberId != null) {
                memberIds.add(memberId);
            }
        } while (resultSet.next());

        return Optional.of(new Party(partyId, leaderId, memberIds, lastWarpTime));
    }

    @Override
    public CompletableFuture<Boolean> isInParty(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM party_members WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), ResultSet::next, "check player in party");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<Party>> getPlayerParty(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> fetchPartyByPlayerSync(playerId), databaseExecutor);
    }

    private Optional<Party> fetchPartyByPlayerSync(@NonNull UUID playerId) {
        String sql = """
                SELECT p.party_id, p.leader_id, p.last_warp_time,
                       pm.player_id
                FROM party_members pm_leader
                JOIN parties p ON pm_leader.party_id = p.party_id
                LEFT JOIN party_members pm ON p.party_id = pm.party_id
                WHERE pm_leader.player_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return buildPartyFromResultSet(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to fetch player party", exception);
        }
    }

    @Override
    public CompletableFuture<Boolean> removePendingInvitation(@NonNull UUID partyId, @NonNull UUID senderId, @NonNull UUID targetId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM party_invitations WHERE party_id = ? AND sender_id = ? AND target_id = ?";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, partyId);
                statement.setObject(2, senderId);
                statement.setObject(3, targetId);
            }, "remove pending invitation");
            return rows > 0;
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<PartyInvitation>> fetchInvitation(@NonNull UUID partyId, @NonNull UUID senderId, @NonNull UUID targetId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT party_id, sender_id, target_id, created_at FROM party_invitations WHERE party_id = ? AND sender_id = ? AND target_id = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, partyId);
                statement.setObject(2, senderId);
                statement.setObject(3, targetId);
            }, resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPartyInvitation(resultSet));
                }
                return Optional.empty();
            }, "fetch invitation");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<List<PartyInvitation>> fetchIncomingInvitations(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT party_id, sender_id, target_id, created_at FROM party_invitations WHERE target_id = ? ORDER BY created_at DESC";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                List<PartyInvitation> invitations = new ArrayList<>();
                while (resultSet.next()) {
                    invitations.add(mapResultSetToPartyInvitation(resultSet));
                }
                return invitations;
            }, "fetch incoming invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<List<PartyInvitation>> fetchOutgoingInvitations(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT party_id, sender_id, target_id, created_at FROM party_invitations WHERE sender_id = ? ORDER BY created_at DESC";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                List<PartyInvitation> invitations = new ArrayList<>();
                while (resultSet.next()) {
                    invitations.add(mapResultSetToPartyInvitation(resultSet));
                }
                return invitations;
            }, "fetch outgoing invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<List<PartyInvitation>> fetchPartyOutgoingInvitations(@NonNull UUID partyId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT party_id, sender_id, target_id, created_at FROM party_invitations WHERE party_id = ? ORDER BY created_at DESC";
            return executeQuery(sql, statement -> statement.setObject(1, partyId), resultSet -> {
                List<PartyInvitation> invitations = new ArrayList<>();
                while (resultSet.next()) {
                    invitations.add(mapResultSetToPartyInvitation(resultSet));
                }
                return invitations;
            }, "fetch party outgoing invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<PartyInvitation>> findInvitationFromLeader(@NonNull UUID inviteeId, @NonNull UUID leaderId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT party_id, sender_id, target_id, created_at FROM party_invitations WHERE target_id = ? AND sender_id = ? ORDER BY created_at DESC LIMIT 1";
            return executeQuery(sql, statement -> {
                statement.setObject(1, inviteeId);
                statement.setObject(2, leaderId);
            }, resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPartyInvitation(resultSet));
                }
                return Optional.empty();
            }, "find invitation from leader");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<PartyInvitation>> findInvitationForParty(@NonNull UUID inviteeId, @NonNull UUID partyId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT party_id, sender_id, target_id, created_at FROM party_invitations WHERE target_id = ? AND party_id = ? ORDER BY created_at DESC LIMIT 1";
            return executeQuery(sql, statement -> {
                statement.setObject(1, inviteeId);
                statement.setObject(2, partyId);
            }, resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPartyInvitation(resultSet));
                }
                return Optional.empty();
            }, "find invitation for party");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> hasInvitation(@NonNull UUID inviteeId, @NonNull UUID partyId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM party_invitations WHERE target_id = ? AND party_id = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, inviteeId);
                statement.setObject(2, partyId);
            }, ResultSet::next, "check has invitation");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Integer> countIncomingInvitations(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM party_invitations WHERE target_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                resultSet.next();
                return resultSet.getInt(1);
            }, "count incoming invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Integer> countOutgoingInvitations(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM party_invitations WHERE sender_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                resultSet.next();
                return resultSet.getInt(1);
            }, "count outgoing invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<ConfirmationOutcome> setPendingConfirmation(@NonNull PendingConfirmation confirmation) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // Try to get existing confirmation first
                    String selectSql = "SELECT confirmation_type, target_id, created_at FROM party_confirmations WHERE player_id = ?";
                    Optional<PendingConfirmation> existingOpt;
                    try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                        statement.setObject(1, confirmation.playerId());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                String typeStr = resultSet.getString("confirmation_type");
                                ConfirmationType type = ConfirmationType.valueOf(typeStr);
                                UUID targetId = (UUID) resultSet.getObject("target_id");
                                Instant timestamp = resultSet.getTimestamp("created_at").toInstant();
                                existingOpt = Optional.of(new PendingConfirmation(confirmation.playerId(), type, targetId, timestamp));
                            } else {
                                existingOpt = Optional.empty();
                            }
                        }
                    }

                    if (existingOpt.isPresent()) {
                        connection.rollback();
                        return new ConfirmationOutcome.AlreadyExists(existingOpt.get());
                    }

                    // No existing confirmation - insert new one
                    String insertSql = "INSERT INTO party_confirmations (player_id, confirmation_type, target_id, created_at) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                        statement.setObject(1, confirmation.playerId());
                        statement.setString(2, confirmation.type().name());
                        statement.setObject(3, confirmation.targetId());
                        statement.setTimestamp(4, Timestamp.from(confirmation.timestamp()));
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new ConfirmationOutcome.Set();
                } catch (SQLException e) {
                    connection.rollback();
                    throw new RuntimeException("Failed to set pending confirmation", e);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set pending confirmation", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> removePendingConfirmation(@NonNull UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM party_confirmations WHERE player_id = ?";
            executeUpdate(sql, statement -> statement.setObject(1, playerId), "remove pending confirmation");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<PendingConfirmation>> fetchPendingConfirmation(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_id, confirmation_type, target_id, created_at FROM party_confirmations WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPendingConfirmation(resultSet));
                }
                return Optional.empty();
            }, "fetch pending confirmation");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<PartySettings>> fetchSettings(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_id, allow_chat, allow_warp, invite_privacy FROM party_settings WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPartySettings(resultSet));
                }
                return Optional.empty();
            }, "fetch settings");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> saveSettings(@NonNull UUID playerId, @NonNull PartySettings settings) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO party_settings (player_id, allow_chat, allow_warp, invite_privacy)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET
                        allow_chat = EXCLUDED.allow_chat,
                        allow_warp = EXCLUDED.allow_warp,
                        invite_privacy = EXCLUDED.invite_privacy
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, settings.allowChat());
                statement.setBoolean(3, settings.allowWarp());
                statement.setString(4, settings.invitePrivacy());
            }, "save settings");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateAllowChat(@NonNull UUID playerId, boolean allowChat) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO party_settings (player_id, allow_chat, allow_warp, invite_privacy)
                    VALUES (?, ?, TRUE, 'all')
                    ON CONFLICT (player_id) DO UPDATE SET allow_chat = EXCLUDED.allow_chat
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, allowChat);
            }, "update allow_chat");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateAllowWarp(@NonNull UUID playerId, boolean allowWarp) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO party_settings (player_id, allow_chat, allow_warp, invite_privacy)
                    VALUES (?, TRUE, ?, 'all')
                    ON CONFLICT (player_id) DO UPDATE SET allow_warp = EXCLUDED.allow_warp
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, allowWarp);
            }, "update allow_warp");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateInvitePrivacy(@NonNull UUID playerId, @NonNull String invitePrivacy) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO party_settings (player_id, allow_chat, allow_warp, invite_privacy)
                    VALUES (?, TRUE, TRUE, ?)
                    ON CONFLICT (player_id) DO UPDATE SET invite_privacy = EXCLUDED.invite_privacy
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, invitePrivacy);
            }, "update invite_privacy");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> recordInvitationCooldown(@NonNull UUID playerA, @NonNull UUID playerB, @NonNull Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            CooldownKey.CanonicalKey key = CooldownKey.canonicalize(playerA, playerB);
            String sql = "INSERT INTO party_cooldowns (player_a, player_b, timestamp) VALUES (?, ?, ?) ON CONFLICT (player_a, player_b) DO UPDATE SET timestamp = EXCLUDED.timestamp";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, key.smaller());
                statement.setObject(2, key.larger());
                statement.setTimestamp(3, Timestamp.from(now));
            }, "record invitation cooldown");
            return rows > 0;
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<Instant>> fetchInvitationCooldown(@NonNull UUID playerA, @NonNull UUID playerB) {
        return CompletableFuture.supplyAsync(() -> {
            CooldownKey.CanonicalKey key = CooldownKey.canonicalize(playerA, playerB);
            String sql = "SELECT timestamp FROM party_cooldowns WHERE player_a = ? AND player_b = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, key.smaller());
                statement.setObject(2, key.larger());
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
            String sql = "DELETE FROM party_invitations WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredConfirmations(@NonNull Instant now, @NonNull Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM party_confirmations WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired confirmations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredCooldowns(@NonNull Instant now, @NonNull Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM party_cooldowns WHERE timestamp < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired cooldowns");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupOrphanedSettings() {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM party_settings ps WHERE NOT EXISTS (SELECT 1 FROM party_members pm WHERE pm.player_id = ps.player_id)";
            executeUpdate(sql, null, "cleanup orphaned settings");
        }, databaseExecutor);
    }

    @Contract("_ -> new")
    private @NonNull PartyInvitation mapResultSetToPartyInvitation(@NonNull ResultSet resultSet) throws SQLException {
        return new PartyInvitation(
                (UUID) resultSet.getObject("party_id"),
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
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    @Contract("_ -> new")
    private @NonNull PartySettings mapResultSetToPartySettings(@NonNull ResultSet resultSet) throws SQLException {
        return new PartySettings(
                (UUID) resultSet.getObject("player_id"),
                resultSet.getBoolean("allow_chat"),
                resultSet.getBoolean("allow_warp"),
                resultSet.getString("invite_privacy")
        );
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
