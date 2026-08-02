package net.valoury.parties.proxy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.*;
import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;
import net.valoury.shared.model.GroupJoinPolicy;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PartyPostgresStorage implements PartyStorage, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyPostgresStorage.class);

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    public PartyPostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(PartyProxyConstants.DATABASE_CONNECTION_POOL_SIZE);
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
                "parties-database-",
                PartyProxyConstants.DATABASE_EXECUTOR_POOL_SIZE
        );
        try {
            initializeSchema();
        } catch (RuntimeException exception) {
            // Clean up resources if schema initialization fails
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

    private static boolean columnExists(
            Connection connection,
            String tableName,
            String columnName
    ) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = ?
                      AND column_name = ?
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean columnIsNullable(
            Connection connection,
            String tableName,
            String columnName
    ) throws SQLException {
        String sql = """
                SELECT is_nullable = 'YES'
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(PartyProxyConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            LOGGER.error("Interrupted while shutting down database executor", exception);
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (schemaExists(connection, "parties")) {
                if (!columnExists(connection, "party_members", "is_moderator")
                        || !columnExists(connection, "party_settings", "auto_warp")
                        || !schemaExists(connection, "party_group_settings")
                        || !columnIsNullable(connection, "party_invitations", "party_id")) {
                    throw new IllegalStateException(
                            "Existing party schema does not support the current party lifecycle");
                }
                return;
            }
            // STEP 1: Create party_members WITHOUT FK to parties (avoids circular dependency)
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_members (
                        party_id UUID NOT NULL,
                        player_id UUID NOT NULL UNIQUE,
                        is_moderator BOOLEAN NOT NULL DEFAULT FALSE,
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
            } catch (SQLException exception) {
                // Constraint already exists - ignore
                if (!"42710".equals(exception.getSQLState())) {
                    throw exception;
                }
            }

            // Remaining tables (unchanged structure)
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_invitations (
                        party_id UUID REFERENCES parties(party_id) ON DELETE CASCADE,
                        sender_id UUID NOT NULL,
                        target_id UUID NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (sender_id, target_id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_invitations_target ON party_invitations(target_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_invitations_sender ON party_invitations(sender_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_invitations_created ON party_invitations(created_at DESC)");
            statement.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_invitations_party_target
                    ON party_invitations(party_id, target_id)
                    WHERE party_id IS NOT NULL
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_settings (
                        player_id UUID PRIMARY KEY,
                        allow_chat BOOLEAN NOT NULL DEFAULT TRUE,
                        allow_warp BOOLEAN NOT NULL DEFAULT TRUE,
                        auto_warp BOOLEAN NOT NULL DEFAULT FALSE,
                        invite_privacy VARCHAR(8) NOT NULL DEFAULT 'all' CHECK (invite_privacy IN ('all', 'friend', 'none'))
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_group_settings (
                        party_id UUID PRIMARY KEY REFERENCES parties(party_id) ON DELETE CASCADE,
                        join_policy VARCHAR(8) NOT NULL DEFAULT 'private'
                            CHECK (join_policy IN ('private', 'public'))
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
                    CREATE INDEX IF NOT EXISTS idx_party_confirmations_created
                    ON party_confirmations(created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS party_cooldowns (
                        sender_id UUID NOT NULL,
                        receiver_id UUID NOT NULL,
                        timestamp TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (sender_id, receiver_id)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_party_cooldowns_timestamp
                    ON party_cooldowns(timestamp)
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

    // Compound operations

    @Override
    public CompletableFuture<DisbandPartyOutcome> disbandParty(@NonNull UUID partyId, @NonNull UUID leaderId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT pg_advisory_xact_lock(hashtextextended(?, 1))")) {
                        statement.setString(1, partyId.toString());
                        statement.executeQuery();
                    }

                    // Defer FK constraint so we can delete members before the party
                    try (PreparedStatement deferFkStatement = connection.prepareStatement(
                            "SET CONSTRAINTS fk_leader_is_member DEFERRED")) {
                        deferFkStatement.execute();
                    }

                    // 1. Lock party_members first (consistent lock ordering with other methods)
                    String lockMembersSql = "SELECT 1 FROM party_members WHERE party_id = ? FOR UPDATE";
                    try (PreparedStatement statement = connection.prepareStatement(lockMembersSql)) {
                        statement.setObject(1, partyId);
                        statement.executeQuery();
                    }

                    // 2. Verify leader (must be done after locking to prevent TOCTOU)
                    String checkLeaderSql = "SELECT leader_id FROM parties WHERE party_id = ?";
                    UUID currentLeaderId;
                    try (PreparedStatement statement = connection.prepareStatement(checkLeaderSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new DisbandPartyOutcome.PartyNotFound();
                            }
                            currentLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    if (!currentLeaderId.equals(leaderId)) {
                        connection.rollback();
                        return new DisbandPartyOutcome.NotLeader();
                    }

                    // 3. Delete confirmations (subquery reads locked party_members)
                    String deleteConfirmationsSql = """
                            DELETE FROM party_confirmations
                            WHERE player_id IN (SELECT player_id FROM party_members WHERE party_id = ?)
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationsSql)) {
                        statement.setObject(1, partyId);
                        statement.executeUpdate();
                    }

                    // 4. Delete invitations
                    String deleteInvitationsSql = "DELETE FROM party_invitations WHERE party_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteInvitationsSql)) {
                        statement.setObject(1, partyId);
                        statement.executeUpdate();
                    }

                    // 5. Delete members explicitly (no CASCADE needed)
                    String deleteMembersSql = "DELETE FROM party_members WHERE party_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteMembersSql)) {
                        statement.setObject(1, partyId);
                        statement.executeUpdate();
                    }

                    // 6. Delete party with conditional leader check (atomically re-verifies leader)
                    String deletePartySql = "DELETE FROM parties WHERE party_id = ? AND leader_id = ?";
                    int rowsDeleted;
                    try (PreparedStatement statement = connection.prepareStatement(deletePartySql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, leaderId);
                        rowsDeleted = statement.executeUpdate();
                    }

                    if (rowsDeleted == 0) {
                        connection.rollback();
                        // Leader changed between step 2 and step 6, or party was already deleted
                        return new DisbandPartyOutcome.NotLeader();
                    }

                    connection.commit();
                    return new DisbandPartyOutcome.Disbanded();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw new RuntimeException("Failed to disband party", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to disband party", exception);
            }
        });
    }

    @Override
    public CompletableFuture<RemoveMemberOutcome> removeMember(@NonNull UUID partyId, @NonNull UUID memberId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT pg_advisory_xact_lock(hashtextextended(?, 1))")) {
                        statement.setString(1, partyId.toString());
                        statement.executeQuery();
                    }

                    // 1. Lock party_members rows first (FOR UPDATE cannot be used with aggregate functions)
                    String lockSql = "SELECT 1 FROM party_members WHERE party_id = ? FOR UPDATE";
                    try (PreparedStatement statement = connection.prepareStatement(lockSql)) {
                        statement.setObject(1, partyId);
                        statement.executeQuery(); // Acquire locks
                    }

                    // 2. Get member count after locking
                    int memberCount;
                    String countSql = "SELECT COUNT(*) FROM party_members WHERE party_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            memberCount = resultSet.getInt(1);
                        }
                    }

                    // 3. Read leader_id AFTER the lock (fixes TOCTOU race)
                    UUID currentLeaderId;
                    String leaderSql = "SELECT leader_id FROM parties WHERE party_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(leaderSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new RemoveMemberOutcome.PartyNotFound();
                            }
                            currentLeaderId = (UUID) resultSet.getObject("leader_id");
                        }
                    }

                    boolean wasLeader = memberId.equals(currentLeaderId);

                    // 4. Delete the member
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

                    PartyMemberPostgresOperations.deleteOutgoingInvitations(
                            connection,
                            partyId,
                            memberId
                    );

                    // 5. Clean up any pending confirmation for the removed member
                    String deleteConfirmationSql = "DELETE FROM party_confirmations WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationSql)) {
                        statement.setObject(1, memberId);
                        statement.executeUpdate();
                    }

                    // 6. Disband instead of allowing a one-member party to survive.
                    if (memberCount <= 2) {
                        try (PreparedStatement statement = connection.prepareStatement("""
                                DELETE FROM party_confirmations
                                WHERE player_id IN (
                                    SELECT player_id FROM party_members WHERE party_id = ?
                                )
                                """)) {
                            statement.setObject(1, partyId);
                            statement.executeUpdate();
                        }
                        String deletePartySql = "DELETE FROM parties WHERE party_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(deletePartySql)) {
                            statement.setObject(1, partyId);
                            statement.executeUpdate();
                        }
                        connection.commit();
                        return new RemoveMemberOutcome.PartyDisbanded();
                    }

                    // 7. If leader left: prefer a moderator, then use stable UUID ordering.
                    if (wasLeader) {
                        String selectNewLeaderSql = """
                                SELECT player_id FROM party_members
                                WHERE party_id = ? AND player_id != ?
                                ORDER BY is_moderator DESC, player_id ASC LIMIT 1
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
                        try (PreparedStatement statement = connection.prepareStatement("""
                                UPDATE party_members
                                SET is_moderator = FALSE
                                WHERE party_id = ? AND player_id = ?
                                """)) {
                            statement.setObject(1, partyId);
                            statement.setObject(2, newLeaderId);
                            statement.executeUpdate();
                        }
                        connection.commit();
                        return new RemoveMemberOutcome.LeaderTransferred(newLeaderId);
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
    public CompletableFuture<KickPartyMemberOutcome> tryKickMember(
            @NonNull UUID partyId,
            @NonNull UUID requesterId,
            @NonNull UUID memberId
    ) {
        return databaseExecutor.supply(() -> PartyMemberPostgresOperations.kickMember(
                dataSource,
                partyId,
                requesterId,
                memberId
        ));
    }

    @Override
    public CompletableFuture<JoinOutcome> acceptInvitationAndJoin(
            @NonNull UUID playerId,
            @NonNull UUID invitationSenderId
    ) {
        return databaseExecutor.supply(() -> PartyInvitationPostgresOperations.acceptInvitation(
                dataSource,
                playerId,
                invitationSenderId
        ));
    }

    @Override
    public CompletableFuture<JoinPublicPartyOutcome> tryJoinPublicParty(
            @NonNull UUID partyId,
            @NonNull UUID expectedLeaderId,
            @NonNull UUID playerId
    ) {
        return databaseExecutor.supply(() -> PartyGroupPostgresOperations.joinPublicParty(
                dataSource,
                partyId,
                expectedLeaderId,
                playerId
        ));
    }

    @Override
    public CompletableFuture<TransferLeadershipOutcome> transferLeadership(@NonNull UUID partyId, @NonNull UUID newLeaderId, @NonNull UUID confirmedByPlayerId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Lock the target membership before the party.
                    String checkMemberSql = """
                            SELECT 1
                            FROM party_members
                            WHERE party_id = ? AND player_id = ?
                            FOR UPDATE
                            """;
                    boolean isMember;
                    try (PreparedStatement statement = connection.prepareStatement(checkMemberSql)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, newLeaderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            isMember = resultSet.next();
                        }
                    }

                    // 2. Lock the party and atomically verify the current leader.
                    UUID currentLeaderId;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT leader_id FROM parties WHERE party_id = ? FOR UPDATE")) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new TransferLeadershipOutcome.PartyNotFound();
                            }
                            currentLeaderId = resultSet.getObject("leader_id", UUID.class);
                        }
                    }
                    if (!currentLeaderId.equals(confirmedByPlayerId)) {
                        connection.rollback();
                        return new TransferLeadershipOutcome.NotLeader();
                    }

                    if (!isMember) {
                        connection.rollback();
                        return new TransferLeadershipOutcome.TargetNotMember();
                    }

                    // 3. Transfer leadership with the leader predicate repeated in the update.
                    String updateLeaderSql = """
                            UPDATE parties
                            SET leader_id = ?
                            WHERE party_id = ? AND leader_id = ?
                            """;
                    int rowsUpdated;
                    try (PreparedStatement statement = connection.prepareStatement(updateLeaderSql)) {
                        statement.setObject(1, newLeaderId);
                        statement.setObject(2, partyId);
                        statement.setObject(3, confirmedByPlayerId);
                        rowsUpdated = statement.executeUpdate();
                    }

                    if (rowsUpdated == 0) {
                        connection.rollback();
                        return new TransferLeadershipOutcome.NotLeader();
                    }

                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE party_members
                            SET is_moderator = FALSE
                            WHERE party_id = ? AND player_id IN (?, ?)
                            """)) {
                        statement.setObject(1, partyId);
                        statement.setObject(2, confirmedByPlayerId);
                        statement.setObject(3, newLeaderId);
                        statement.executeUpdate();
                    }

                    // 4. Delete the completed confirmation.
                    String deleteConfirmationSql = "DELETE FROM party_confirmations WHERE player_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteConfirmationSql)) {
                        statement.setObject(1, confirmedByPlayerId);
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
    public CompletableFuture<PartyModeratorChangeOutcome> updateModeratorStatus(
            @NonNull UUID partyId,
            @NonNull UUID leaderId,
            @NonNull UUID memberId,
            boolean moderator
    ) {
        return databaseExecutor.supply(() -> PartyMemberPostgresOperations.updateModeratorStatus(
                dataSource,
                partyId,
                leaderId,
                memberId,
                moderator
        ));
    }

    @Override
    public CompletableFuture<WarpOutcome> checkAndUpdateLastWarpTime(
            @NonNull UUID partyId,
            @NonNull UUID leaderId,
            @NonNull Instant now,
            @NonNull Duration cooldown
    ) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // 1. Lock the party and verify the current leader.
                    Optional<Instant> lastWarpOptional;
                    String selectSql = """
                            SELECT leader_id, last_warp_time
                            FROM parties
                            WHERE party_id = ?
                            FOR UPDATE
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                        statement.setObject(1, partyId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                UUID currentLeaderId = resultSet.getObject("leader_id", UUID.class);
                                if (!currentLeaderId.equals(leaderId)) {
                                    connection.rollback();
                                    return new WarpOutcome.NotLeader();
                                }
                                Timestamp lastWarpTimestamp = resultSet.getTimestamp("last_warp_time");
                                lastWarpOptional = Optional.ofNullable(lastWarpTimestamp).map(Timestamp::toInstant);
                            } else {
                                connection.rollback();
                                return new WarpOutcome.PartyNotFound();
                            }
                        }
                    }

                    // 2. If cooldown has not elapsed, do not update it.
                    if (lastWarpOptional.isPresent()) {
                        Instant cooldownEnd = lastWarpOptional.get().plus(cooldown);
                        if (now.isBefore(cooldownEnd)) {
                            connection.rollback();
                            return new WarpOutcome.OnCooldown();
                        }
                    }

                    // 3. Repeat the leader predicate in the update.
                    String updateSql = """
                            UPDATE parties
                            SET last_warp_time = ?
                            WHERE party_id = ? AND leader_id = ?
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                        statement.setTimestamp(1, Timestamp.from(now));
                        statement.setObject(2, partyId);
                        statement.setObject(3, leaderId);
                        if (statement.executeUpdate() != 1) {
                            connection.rollback();
                            return new WarpOutcome.NotLeader();
                        }
                    }

                    connection.commit();
                    return new WarpOutcome.Allowed();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw new RuntimeException("Failed to check and update warp time", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to check and update warp time", exception);
            }
        });
    }

    @Override
    public CompletableFuture<Map<UUID, PartySettings>> fetchSettingsForMembers(@NonNull Collection<UUID> memberIds) {
        return databaseExecutor.supply(() -> {
            if (memberIds.isEmpty()) {
                return Map.of();
            }

            String sql = """
                    SELECT player_id, allow_chat, allow_warp, auto_warp, invite_privacy
                    FROM party_settings
                    WHERE player_id = ANY(?)
                    """;
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
        });
    }

    @Override
    public CompletableFuture<SendInvitationOutcome> trySendInvitation(
            @NonNull Optional<UUID> expectedPartyId,
            @NonNull UUID senderId,
            @NonNull UUID targetId,
            boolean isFriend
    ) {
        return databaseExecutor.supply(() -> PartyInvitationPostgresOperations.sendInvitation(
                dataSource,
                expectedPartyId,
                senderId,
                targetId,
                isFriend
        ));
    }

    @Override
    public CompletableFuture<RevokePartyInvitationOutcome> tryRevokeInvitation(
            @NonNull Optional<UUID> partyId,
            @NonNull UUID requesterId,
            @NonNull UUID targetId
    ) {
        return partyId
                .map(existingPartyId -> databaseExecutor.supply(
                        () -> PartyMemberPostgresOperations.revokeInvitation(
                                dataSource,
                                existingPartyId,
                                requesterId,
                                targetId
                        )))
                .orElseGet(() -> databaseExecutor.supply(() -> {
                    String sql = """
                            DELETE FROM party_invitations
                            WHERE party_id IS NULL AND sender_id = ? AND target_id = ?
                            """;
                    int deletedRows = executeUpdate(sql, statement -> {
                        statement.setObject(1, requesterId);
                        statement.setObject(2, targetId);
                    }, "revoke standalone party invitation");
                    return deletedRows > 0
                            ? new RevokePartyInvitationOutcome.Revoked()
                            : new RevokePartyInvitationOutcome.InvitationNotFound();
                }));
    }

    // Single-query operations

    @Override
    public CompletableFuture<Optional<Party>> fetchParty(@NonNull UUID partyId) {
        return databaseExecutor.supply(() -> fetchPartySync(partyId));
    }

    private Optional<Party> fetchPartySync(@NonNull UUID partyId) {
        String sql = """
                SELECT p.party_id, p.leader_id, p.last_warp_time,
                       pm.player_id, pm.is_moderator
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
        Set<UUID> moderatorIds = new HashSet<>();
        do {
            UUID memberId = (UUID) resultSet.getObject("player_id");
            if (memberId != null) {
                memberIds.add(memberId);
                if (resultSet.getBoolean("is_moderator")) {
                    moderatorIds.add(memberId);
                }
            }
        } while (resultSet.next());

        return Optional.of(new Party(
                partyId,
                leaderId,
                memberIds,
                moderatorIds,
                lastWarpTime
        ));
    }

    @Override
    public CompletableFuture<Boolean> isInParty(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT 1 FROM party_members WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), ResultSet::next, "check player in party");
        });
    }

    @Override
    public CompletableFuture<Optional<Party>> getPlayerParty(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> fetchPartyByPlayerSync(playerId));
    }

    private Optional<Party> fetchPartyByPlayerSync(@NonNull UUID playerId) {
        String sql = """
                SELECT p.party_id, p.leader_id, p.last_warp_time,
                       pm.player_id, pm.is_moderator
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
    public CompletableFuture<Boolean> removePendingInvitation(
            @NonNull UUID senderId,
            @NonNull UUID targetId
    ) {
        return databaseExecutor.supply(() -> {
            String sql = "DELETE FROM party_invitations WHERE sender_id = ? AND target_id = ?";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, senderId);
                statement.setObject(2, targetId);
            }, "remove pending invitation");
            return rows > 0;
        });
    }

    @Override
    public CompletableFuture<List<PartyInvitation>> fetchIncomingInvitations(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT party_id, sender_id, target_id, created_at FROM party_invitations WHERE target_id = ? ORDER BY created_at DESC";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                List<PartyInvitation> invitations = new ArrayList<>();
                while (resultSet.next()) {
                    invitations.add(mapResultSetToPartyInvitation(resultSet));
                }
                return invitations;
            }, "fetch incoming invitations");
        });
    }

    @Override
    public CompletableFuture<List<PartyInvitation>> fetchOutgoingInvitations(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT party_id, sender_id, target_id, created_at FROM party_invitations WHERE sender_id = ? ORDER BY created_at DESC";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                List<PartyInvitation> invitations = new ArrayList<>();
                while (resultSet.next()) {
                    invitations.add(mapResultSetToPartyInvitation(resultSet));
                }
                return invitations;
            }, "fetch outgoing invitations");
        });
    }

    @Override
    public CompletableFuture<Optional<PartyInvitation>> findInvitationFromSender(@NonNull UUID inviteeId, @NonNull UUID senderId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT party_id, sender_id, target_id, created_at FROM party_invitations WHERE target_id = ? AND sender_id = ? ORDER BY created_at DESC LIMIT 1";
            return executeQuery(sql, statement -> {
                statement.setObject(1, inviteeId);
                statement.setObject(2, senderId);
            }, resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPartyInvitation(resultSet));
                }
                return Optional.empty();
            }, "find invitation from leader");
        });
    }

    @Override
    public CompletableFuture<Integer> countIncomingInvitations(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT COUNT(*) FROM party_invitations WHERE target_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                resultSet.next();
                return resultSet.getInt(1);
            }, "count incoming invitations");
        });
    }

    @Override
    public CompletableFuture<Integer> countOutgoingInvitations(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT COUNT(*) FROM party_invitations WHERE sender_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                resultSet.next();
                return resultSet.getInt(1);
            }, "count outgoing invitations");
        });
    }

    @Override
    public CompletableFuture<ConfirmationOutcome> setPendingConfirmation(@NonNull PendingConfirmation confirmation) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // Try to get existing confirmation first
                    String selectSql = "SELECT confirmation_type, target_id, created_at FROM party_confirmations WHERE player_id = ?";
                    Optional<PendingConfirmation> existingOptional;
                    try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                        statement.setObject(1, confirmation.playerId());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                String typeStr = resultSet.getString("confirmation_type");
                                ConfirmationType type = ConfirmationType.valueOf(typeStr);
                                UUID targetId = (UUID) resultSet.getObject("target_id");
                                Instant timestamp = resultSet.getTimestamp("created_at").toInstant();
                                existingOptional = Optional.of(new PendingConfirmation(confirmation.playerId(), type, targetId, timestamp));
                            } else {
                                existingOptional = Optional.empty();
                            }
                        }
                    }

                    if (existingOptional.isPresent()) {
                        connection.rollback();
                        return new ConfirmationOutcome.AlreadyExists(existingOptional.get());
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
                } catch (SQLException exception) {
                    connection.rollback();
                    // Check for unique violation - another transaction inserted first
                    if ("23505".equals(exception.getSQLState())) {
                        // Re-fetch the existing confirmation that caused the conflict
                        String selectSql = "SELECT confirmation_type, target_id, created_at FROM party_confirmations WHERE player_id = ?";
                        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                            statement.setObject(1, confirmation.playerId());
                            try (ResultSet resultSet = statement.executeQuery()) {
                                if (resultSet.next()) {
                                    String typeStr = resultSet.getString("confirmation_type");
                                    ConfirmationType type = ConfirmationType.valueOf(typeStr);
                                    UUID targetId = (UUID) resultSet.getObject("target_id");
                                    Instant timestamp = resultSet.getTimestamp("created_at").toInstant();
                                    PendingConfirmation existing = new PendingConfirmation(confirmation.playerId(), type, targetId, timestamp);
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
            String sql = "DELETE FROM party_confirmations WHERE player_id = ?";
            executeUpdate(sql, statement -> statement.setObject(1, playerId), "remove pending confirmation");
        });
    }

    @Override
    public CompletableFuture<Optional<PendingConfirmation>> fetchPendingConfirmation(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT player_id, confirmation_type, target_id, created_at FROM party_confirmations WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPendingConfirmation(resultSet));
                }
                return Optional.empty();
            }, "fetch pending confirmation");
        });
    }

    @Override
    public CompletableFuture<Optional<PartySettings>> fetchSettings(@NonNull UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    SELECT player_id, allow_chat, allow_warp, auto_warp, invite_privacy
                    FROM party_settings
                    WHERE player_id = ?
                    """;
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToPartySettings(resultSet));
                }
                return Optional.empty();
            }, "fetch settings");
        });
    }

    @Override
    public CompletableFuture<Optional<PartyGroupSettings>> fetchGroupSettings(
            @NonNull UUID partyId
    ) {
        return databaseExecutor.supply(() ->
                PartyGroupPostgresOperations.fetchSettings(dataSource, partyId));
    }

    @Override
    public CompletableFuture<Void> updateAllowChat(@NonNull UUID playerId, boolean allowChat) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO party_settings (player_id, allow_chat, allow_warp, invite_privacy)
                    VALUES (?, ?, TRUE, 'all')
                    ON CONFLICT (player_id) DO UPDATE SET allow_chat = EXCLUDED.allow_chat
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, allowChat);
            }, "update allow_chat");
        });
    }

    @Override
    public CompletableFuture<Void> updateAllowWarp(@NonNull UUID playerId, boolean allowWarp) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO party_settings (player_id, allow_chat, allow_warp, invite_privacy)
                    VALUES (?, TRUE, ?, 'all')
                    ON CONFLICT (player_id) DO UPDATE SET allow_warp = EXCLUDED.allow_warp
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, allowWarp);
            }, "update allow_warp");
        });
    }

    @Override
    public CompletableFuture<Void> updateAutoWarp(@NonNull UUID playerId, boolean autoWarp) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO party_settings (player_id, auto_warp) VALUES (?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET auto_warp = EXCLUDED.auto_warp
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, autoWarp);
            }, "update auto warp");
        });
    }

    @Override
    public CompletableFuture<Void> updateInvitePrivacy(@NonNull UUID playerId, @NonNull String invitePrivacy) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO party_settings (player_id, allow_chat, allow_warp, invite_privacy)
                    VALUES (?, TRUE, TRUE, ?)
                    ON CONFLICT (player_id) DO UPDATE SET invite_privacy = EXCLUDED.invite_privacy
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, invitePrivacy);
            }, "update invite_privacy");
        });
    }

    @Override
    public CompletableFuture<UpdatePartyJoinPolicyOutcome> updateJoinPolicy(
            @NonNull UUID partyId,
            @NonNull UUID requesterId,
            @NonNull GroupJoinPolicy joinPolicy
    ) {
        return databaseExecutor.supply(() -> PartyGroupPostgresOperations.updateJoinPolicy(
                dataSource,
                partyId,
                requesterId,
                joinPolicy
        ));
    }

    @Override
    public CompletableFuture<Boolean> recordInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId, @NonNull Instant now) {
        return databaseExecutor.supply(() -> {
            String sql = "INSERT INTO party_cooldowns (sender_id, receiver_id, timestamp) VALUES (?, ?, ?) ON CONFLICT (sender_id, receiver_id) DO UPDATE SET timestamp = EXCLUDED.timestamp";
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
        });
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredInvitations(@NonNull Instant now, @NonNull Duration expiry) {
        return databaseExecutor.run(() -> {
            String sql = """
                    DELETE FROM party_invitations AS invitation
                    WHERE invitation.created_at < ?
                       OR (
                           invitation.party_id IS NOT NULL
                           AND NOT EXISTS (
                               SELECT 1
                               FROM party_members AS member
                               WHERE member.party_id = invitation.party_id
                                 AND member.player_id = invitation.sender_id
                           )
                       )
                    """;
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired invitations");
        });
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredConfirmations(@NonNull Instant now, @NonNull Duration expiry) {
        return databaseExecutor.run(() -> {
            String sql = "DELETE FROM party_confirmations WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired confirmations");
        });
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredCooldowns(@NonNull Instant now, @NonNull Duration expiry) {
        return databaseExecutor.run(() -> {
            String sql = "DELETE FROM party_cooldowns WHERE timestamp < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired cooldowns");
        });
    }

    @Contract("_ -> new")
    private @NonNull PartyInvitation mapResultSetToPartyInvitation(@NonNull ResultSet resultSet) throws SQLException {
        return new PartyInvitation(
                Optional.ofNullable(resultSet.getObject("party_id", UUID.class)),
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
                resultSet.getBoolean("auto_warp"),
                resultSet.getString("invite_privacy")
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
