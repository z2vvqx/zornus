package com.zornus.parties.proxy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.*;
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
import java.util.function.Predicate;

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
        initializeSchema();
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
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS parties (
                        party_id UUID PRIMARY KEY,
                        party_name VARCHAR(64) NOT NULL,
                        leader_id UUID NOT NULL,
                        leader_name VARCHAR(16) NOT NULL,
                        last_warp_time TIMESTAMPTZ
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_members (
                        party_id UUID NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
                        player_id UUID NOT NULL,
                        player_name VARCHAR(16) NOT NULL,
                        PRIMARY KEY (party_id, player_id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_party_members_player ON party_members(player_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_party_members_party ON party_members(party_id)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_parties (
                        player_id UUID PRIMARY KEY,
                        party_id UUID NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_invitations (
                        party_id UUID NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
                        party_name VARCHAR(64) NOT NULL,
                        sender_id UUID NOT NULL,
                        sender_name VARCHAR(16) NOT NULL,
                        target_id UUID NOT NULL,
                        target_name VARCHAR(16) NOT NULL,
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
                        target_name VARCHAR(16),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_cooldowns (
                        sender_id UUID NOT NULL,
                        receiver_id UUID NOT NULL,
                        timestamp TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (sender_id, receiver_id)
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
                    // 1. INSERT INTO parties
                    String insertPartySql = "INSERT INTO parties (party_id, party_name, leader_id, leader_name, last_warp_time) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertPartySql)) {
                        statement.setObject(1, party.partyId());
                        statement.setString(2, party.partyName());
                        statement.setObject(3, party.leaderId());
                        statement.setString(4, party.leaderName());
                        Optional<Instant> lastWarp = party.lastWarpTime();
                        statement.setTimestamp(5, lastWarp.map(Timestamp::from).orElse(null));
                        statement.executeUpdate();
                    }

                    // 2. INSERT INTO party_members for leader
                    String insertMemberSql = "INSERT INTO party_members (party_id, player_id, player_name) VALUES (?, ?, ?)";
                    try (PreparedStatement statement = connection.prepareStatement(insertMemberSql)) {
                        statement.setObject(1, party.partyId());
                        statement.setObject(2, party.leaderId());
                        statement.setString(3, party.leaderName());
                        statement.executeUpdate();
                    }

                    // 3. INSERT INTO player_parties with ON CONFLICT DO NOTHING
                    String insertPlayerPartySql = "INSERT INTO player_parties (player_id, party_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
                    int rowsInserted;
                    try (PreparedStatement statement = connection.prepareStatement(insertPlayerPartySql)) {
                        statement.setObject(1, party.leaderId());
                        statement.setObject(2, party.partyId());
                        rowsInserted = statement.executeUpdate();
                    }

                    // 4. If 0 rows on player_parties: rollback, return AlreadyInParty
                    if (rowsInserted == 0) {
                        connection.rollback();
                        return new CreatePartyOutcome.AlreadyInParty();
                    }

                    connection.commit();
                    return new CreatePartyOutcome.Created();
                } catch (SQLException e) {
                    connection.rollback();
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
                    // 1. DELETE FROM party_confirmations WHERE player_id = leaderId
                    String deleteConfirmationsSql = "DELETE FROM party_confirmations WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationsSql)) {
                        statement.setObject(1, leaderId);
                        statement.executeUpdate();
                    }

                    // 2. DELETE FROM parties WHERE party_id = ? — if 0 rows: rollback, return PartyNotFound
                    // Note: party_invitations are cascade-deleted via FK constraint
                    String deletePartySql = "DELETE FROM parties WHERE party_id = ?";
                    int rowsDeleted;
                    try (PreparedStatement statement = connection.prepareStatement(deletePartySql)) {
                        statement.setObject(1, partyId);
                        rowsDeleted = statement.executeUpdate();
                    }

                    if (rowsDeleted == 0) {
                        connection.rollback();
                        return new DisbandPartyOutcome.PartyNotFound();
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
    public CompletableFuture<RemoveMemberOutcome> removeMember(@NonNull UUID partyId, @NonNull UUID memberId,
                                                                @Nullable UUID newLeaderId, @Nullable String newLeaderName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. SELECT COUNT(*) FROM party_members WHERE party_id = ? FOR UPDATE
                    int memberCount;
                    String countSql = "SELECT COUNT(*) FROM party_members WHERE party_id = ? FOR UPDATE";
                    try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            memberCount = resultSet.getInt(1);
                        }
                    }

                    // 2. If count == 0: return MemberNotFound
                    if (memberCount == 0) {
                        connection.rollback();
                        return new RemoveMemberOutcome.MemberNotFound();
                    }

                    // 3. DELETE FROM party_members WHERE party_id = ? AND player_id = ? — if 0 rows: return MemberNotFound
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

                    // 4. DELETE FROM player_parties WHERE player_id = ?
                    String deletePlayerPartySql = "DELETE FROM player_parties WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deletePlayerPartySql)) {
                        statement.setObject(1, memberId);
                        statement.executeUpdate();
                    }

                    // 5. If count was 1: DELETE FROM parties WHERE party_id = ? → return PartyDisbanded
                    if (memberCount == 1) {
                        String deletePartySql = "DELETE FROM parties WHERE party_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(deletePartySql)) {
                            statement.setObject(1, partyId);
                            statement.executeUpdate();
                        }
                        connection.commit();
                        return new RemoveMemberOutcome.PartyDisbanded();
                    }

                    // 6. Else if newLeaderId != null: UPDATE parties SET leader_id = ?, leader_name = ? → return LeaderTransferred
                    if (newLeaderId != null) {
                        String updateLeaderSql = "UPDATE parties SET leader_id = ?, leader_name = ? WHERE party_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(updateLeaderSql)) {
                            statement.setObject(1, newLeaderId);
                            statement.setString(2, newLeaderName);
                            statement.setObject(3, partyId);
                            statement.executeUpdate();
                        }
                        connection.commit();
                        return new RemoveMemberOutcome.LeaderTransferred(newLeaderId);
                    }

                    // 7. Else: return MemberRemoved
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
    public CompletableFuture<JoinOutcome> acceptInvitationAndJoin(@NonNull UUID partyId, @NonNull UUID playerId,
                                                                   @NonNull String playerName, @NonNull UUID invitationSenderId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Check if player is already in another party
                    String checkPlayerPartySql = "SELECT 1 FROM player_parties WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkPlayerPartySql)) {
                        statement.setObject(1, playerId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new JoinOutcome.AlreadyMember();
                            }
                        }
                    }

                    // 2. SELECT COUNT(*) FROM party_members WHERE party_id = ? FOR UPDATE
                    int memberCount;
                    String countSql = "SELECT COUNT(*) FROM party_members WHERE party_id = ? FOR UPDATE";
                    try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            memberCount = resultSet.getInt(1);
                        }
                    }

                    // 3. If count >= MAX_PARTY_SIZE: rollback, return PartyFull
                    if (memberCount >= PartyProxyConstants.MAX_PARTY_SIZE) {
                        connection.rollback();
                        return new JoinOutcome.PartyFull();
                    }

                    // 4. INSERT INTO party_members ON CONFLICT DO NOTHING — if 0 rows: rollback, return AlreadyMember
                    String insertMemberSql = "INSERT INTO party_members (party_id, player_id, player_name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
                    int rowsInserted;
                    try (PreparedStatement statement = connection.prepareStatement(insertMemberSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, playerId);
                        statement.setString(3, playerName);
                        rowsInserted = statement.executeUpdate();
                    }

                    if (rowsInserted == 0) {
                        connection.rollback();
                        return new JoinOutcome.AlreadyMember();
                    }

                    // 5. INSERT INTO player_parties ON CONFLICT DO UPDATE
                    String insertPlayerPartySql = "INSERT INTO player_parties (player_id, party_id) VALUES (?, ?) ON CONFLICT (player_id) DO UPDATE SET party_id = EXCLUDED.party_id";
                    try (PreparedStatement statement = connection.prepareStatement(insertPlayerPartySql)) {
                        statement.setObject(1, playerId);
                        statement.setObject(2, partyId);
                        statement.executeUpdate();
                    }

                    // 6. DELETE FROM party_invitations WHERE party_id = ? AND sender_id = ? AND target_id = ?
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
                    throw new RuntimeException("Failed to accept invitation and join", e);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to accept invitation and join", e);
            }
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<TransferLeadershipOutcome> transferLeadership(@NonNull UUID partyId, @NonNull UUID newLeaderId,
                                                                            @NonNull String newLeaderName, @NonNull UUID confirmedByPlayerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Verify target is still a party member
                    String checkMemberSql = "SELECT 1 FROM party_members WHERE party_id = ? AND player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkMemberSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, newLeaderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new TransferLeadershipOutcome.PartyNotFound();
                            }
                        }
                    }

                    // 2. UPDATE parties SET leader_id = ?, leader_name = ? WHERE party_id = ? — if 0 rows: rollback, return PartyNotFound
                    String updateLeaderSql = "UPDATE parties SET leader_id = ?, leader_name = ? WHERE party_id = ?";
                    int rowsUpdated;
                    try (PreparedStatement statement = connection.prepareStatement(updateLeaderSql)) {
                        statement.setObject(1, newLeaderId);
                        statement.setString(2, newLeaderName);
                        statement.setObject(3, partyId);
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
    public CompletableFuture<SendInvitationOutcome> trySendInvitation(@NonNull UUID partyId, @NonNull String partyName,
                                                                       @NonNull UUID senderId, @NonNull String senderName,
                                                                       @NonNull UUID targetId, @NonNull String targetName,
                                                                       @Nullable Predicate<UUID> isFriendChecker) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Check target privacy settings (atomically, inside transaction)
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
                            if (isFriendChecker == null || !isFriendChecker.test(targetId)) {
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

                    // 2. Check if target is already in a party
                    String checkTargetInPartySql = "SELECT 1 FROM player_parties WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkTargetInPartySql)) {
                        statement.setObject(1, targetId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new SendInvitationOutcome.TargetAlreadyInParty();
                            }
                        }
                    }

                    // 3. Check if party is full
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

                    // 4. Check invitation cooldown
                    String checkCooldownSql = "SELECT timestamp FROM party_cooldowns WHERE sender_id = ? AND receiver_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkCooldownSql)) {
                        statement.setObject(1, senderId);
                        statement.setObject(2, targetId);
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

                    // 8. Insert invitation
                    String insertInviteSql = """
                        INSERT INTO party_invitations (party_id, party_name, sender_id, sender_name, target_id, target_name, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, NOW())
                        """;
                    try (PreparedStatement statement = connection.prepareStatement(insertInviteSql)) {
                        statement.setObject(1, partyId);
                        statement.setString(2, partyName);
                        statement.setObject(3, senderId);
                        statement.setString(4, senderName);
                        statement.setObject(5, targetId);
                        statement.setString(6, targetName);
                        statement.executeUpdate();
                    }

                    // 9. Record/refresh cooldown
                    String upsertCooldownSql = """
                        INSERT INTO party_cooldowns (sender_id, receiver_id, timestamp)
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
                SELECT p.party_id, p.party_name, p.leader_id, p.leader_name, p.last_warp_time,
                       pm.player_id, pm.player_name
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
        String partyName = resultSet.getString("party_name");
        UUID leaderId = (UUID) resultSet.getObject("leader_id");
        String leaderName = resultSet.getString("leader_name");
        Timestamp lastWarpTimestamp = resultSet.getTimestamp("last_warp_time");
        Optional<Instant> lastWarpTime = Optional.ofNullable(lastWarpTimestamp).map(Timestamp::toInstant);

        Map<UUID, String> memberNames = new HashMap<>();
        do {
            UUID memberId = (UUID) resultSet.getObject("player_id");
            String memberName = resultSet.getString("player_name");
            if (memberId != null) {
                memberNames.put(memberId, memberName);
            }
        } while (resultSet.next());

        return Optional.of(new Party(partyId, partyName, leaderId, leaderName, memberNames, lastWarpTime));
    }

    @Override
    public CompletableFuture<Boolean> isInParty(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM player_parties WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), ResultSet::next, "check player in party");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<Party>> getPlayerParty(@NonNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> fetchPartyByPlayerSync(playerId), databaseExecutor);
    }

    private Optional<Party> fetchPartyByPlayerSync(@NonNull UUID playerId) {
        String sql = """
                SELECT p.party_id, p.party_name, p.leader_id, p.leader_name, p.last_warp_time,
                       pm.player_id, pm.player_name
                FROM player_parties pp
                JOIN parties p ON pp.party_id = p.party_id
                LEFT JOIN party_members pm ON p.party_id = pm.party_id
                WHERE pp.player_id = ?
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
    public CompletableFuture<Boolean> addPendingInvitation(@NonNull PartyInvitation invitation) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO party_invitations (party_id, party_name, sender_id, sender_name, target_id, target_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, invitation.partyId());
                statement.setString(2, invitation.partyName());
                statement.setObject(3, invitation.senderId());
                statement.setString(4, invitation.senderName());
                statement.setObject(5, invitation.targetId());
                statement.setString(6, invitation.targetName());
                statement.setTimestamp(7, Timestamp.from(invitation.timestamp()));
            }, "add pending invitation");
            return rows > 0;
        }, databaseExecutor);
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
            String sql = "SELECT party_id, party_name, sender_id, sender_name, target_id, target_name, created_at FROM party_invitations WHERE party_id = ? AND sender_id = ? AND target_id = ?";
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
            String sql = "SELECT party_id, party_name, sender_id, sender_name, target_id, target_name, created_at FROM party_invitations WHERE target_id = ? ORDER BY created_at DESC";
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
            String sql = "SELECT party_id, party_name, sender_id, sender_name, target_id, target_name, created_at FROM party_invitations WHERE sender_id = ? ORDER BY created_at DESC";
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
            String sql = "SELECT party_id, party_name, sender_id, sender_name, target_id, target_name, created_at FROM party_invitations WHERE party_id = ? ORDER BY created_at DESC";
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
            String sql = "SELECT party_id, party_name, sender_id, sender_name, target_id, target_name, created_at FROM party_invitations WHERE target_id = ? AND sender_id = ? ORDER BY created_at DESC LIMIT 1";
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
            String sql = "SELECT party_id, party_name, sender_id, sender_name, target_id, target_name, created_at FROM party_invitations WHERE target_id = ? AND party_id = ? ORDER BY created_at DESC LIMIT 1";
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
    public CompletableFuture<Void> setPendingConfirmation(@NonNull PendingConfirmation confirmation) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO party_confirmations (player_id, confirmation_type, target_id, target_name, created_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (player_id) DO UPDATE SET confirmation_type = EXCLUDED.confirmation_type, target_id = EXCLUDED.target_id, target_name = EXCLUDED.target_name, created_at = EXCLUDED.created_at";
            executeUpdate(sql, statement -> {
                statement.setObject(1, confirmation.playerId());
                statement.setString(2, confirmation.type().name());
                statement.setObject(3, confirmation.targetId());
                statement.setString(4, confirmation.targetName());
                statement.setTimestamp(5, Timestamp.from(confirmation.timestamp()));
            }, "set pending confirmation");
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
            String sql = "SELECT player_id, confirmation_type, target_id, target_name, created_at FROM party_confirmations WHERE player_id = ?";
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
    public CompletableFuture<Boolean> recordInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO party_cooldowns (sender_id, receiver_id, timestamp) VALUES (?, ?, NOW()) ON CONFLICT (sender_id, receiver_id) DO UPDATE SET timestamp = EXCLUDED.timestamp";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, senderId);
                statement.setObject(2, receiverId);
            }, "record invitation cooldown");
            return rows > 0;
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<Instant>> fetchInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT timestamp FROM party_cooldowns WHERE sender_id = ? AND receiver_id = ?";
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
    public CompletableFuture<Void> cleanupExpiredInvitations(@NonNull Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM party_invitations WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(Instant.now().minus(expiry))), "cleanup expired invitations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredConfirmations(@NonNull Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM party_confirmations WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(Instant.now().minus(expiry))), "cleanup expired confirmations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredCooldowns(@NonNull Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM party_cooldowns WHERE timestamp < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(Instant.now().minus(expiry))), "cleanup expired cooldowns");
        }, databaseExecutor);
    }

    @Contract("_ -> new")
    private @NonNull PartyInvitation mapResultSetToPartyInvitation(@NonNull ResultSet resultSet) throws SQLException {
        return new PartyInvitation(
                (UUID) resultSet.getObject("party_id"),
                resultSet.getString("party_name"),
                (UUID) resultSet.getObject("sender_id"),
                resultSet.getString("sender_name"),
                (UUID) resultSet.getObject("target_id"),
                resultSet.getString("target_name"),
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
                resultSet.getString("target_name"),
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
