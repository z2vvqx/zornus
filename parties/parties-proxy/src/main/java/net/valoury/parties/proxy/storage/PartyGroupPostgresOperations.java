package net.valoury.parties.proxy.storage;

import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.PartyGroupSettings;
import net.valoury.shared.model.GroupJoinPolicy;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

final class PartyGroupPostgresOperations {

    private PartyGroupPostgresOperations() {
    }

    static @NonNull Optional<PartyGroupSettings> fetchSettings(
            @NonNull DataSource dataSource,
            @NonNull UUID partyId
    ) {
        String sql = "SELECT join_policy FROM party_group_settings WHERE party_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, partyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PartyGroupSettings(
                        partyId,
                        GroupJoinPolicy.fromStoredValue(resultSet.getString("join_policy"))
                ));
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to fetch party group settings", exception);
        }
    }

    static @NonNull UpdatePartyJoinPolicyOutcome updateJoinPolicy(
            @NonNull DataSource dataSource,
            @NonNull UUID partyId,
            @NonNull UUID requesterId,
            @NonNull GroupJoinPolicy joinPolicy
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<UUID> leaderId = lockAndFetchLeader(connection, partyId);
                if (leaderId.isEmpty()) {
                    connection.rollback();
                    return new UpdatePartyJoinPolicyOutcome.PartyNotFound();
                }
                if (!leaderId.get().equals(requesterId)) {
                    connection.rollback();
                    return new UpdatePartyJoinPolicyOutcome.NotLeader();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE party_group_settings
                        SET join_policy = ?
                        WHERE party_id = ?
                        """)) {
                    statement.setString(1, joinPolicy.storedValue());
                    statement.setObject(2, partyId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Party is missing its group settings row: " + partyId);
                    }
                }

                connection.commit();
                return new UpdatePartyJoinPolicyOutcome.Updated();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to update party join policy", exception);
        }
    }

    static @NonNull JoinPublicPartyOutcome joinPublicParty(
            @NonNull DataSource dataSource,
            @NonNull UUID partyId,
            @NonNull UUID expectedLeaderId,
            @NonNull UUID playerId
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                acquirePartyJoinLock(connection, partyId);

                Optional<JoinableParty> joinableParty = fetchJoinableParty(connection, partyId);
                if (joinableParty.isEmpty()) {
                    connection.rollback();
                    return new JoinPublicPartyOutcome.PartyNotFound();
                }
                if (!joinableParty.get().leaderId().equals(expectedLeaderId)) {
                    connection.rollback();
                    return new JoinPublicPartyOutcome.TargetNotLeader();
                }
                if (isPartyMember(connection, playerId)) {
                    connection.rollback();
                    return new JoinPublicPartyOutcome.AlreadyInParty();
                }
                if (joinableParty.get().joinPolicy() != GroupJoinPolicy.PUBLIC) {
                    connection.rollback();
                    return new JoinPublicPartyOutcome.PartyPrivate();
                }
                if (memberCount(connection, partyId) >= PartyProxyConstants.MAX_PARTY_SIZE) {
                    connection.rollback();
                    return new JoinPublicPartyOutcome.PartyFull();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO party_members (party_id, player_id, is_moderator)
                        VALUES (?, ?, FALSE)
                        """)) {
                    statement.setObject(1, partyId);
                    statement.setObject(2, playerId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM party_invitations
                        WHERE party_id IS NULL AND sender_id = ?
                        """)) {
                    statement.setObject(1, playerId);
                    statement.executeUpdate();
                }

                connection.commit();
                return new JoinPublicPartyOutcome.Joined();
            } catch (SQLException exception) {
                connection.rollback();
                if ("23505".equals(exception.getSQLState())) {
                    return new JoinPublicPartyOutcome.AlreadyInParty();
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to join public party", exception);
        }
    }

    private static @NonNull Optional<UUID> lockAndFetchLeader(
            @NonNull Connection connection,
            @NonNull UUID partyId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT leader_id FROM parties WHERE party_id = ? FOR UPDATE")) {
            statement.setObject(1, partyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getObject("leader_id", UUID.class))
                        : Optional.empty();
            }
        }
    }

    private static void acquirePartyJoinLock(
            @NonNull Connection connection,
            @NonNull UUID partyId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 1))")) {
            statement.setString(1, partyId.toString());
            statement.executeQuery();
        }
    }

    private static @NonNull Optional<JoinableParty> fetchJoinableParty(
            @NonNull Connection connection,
            @NonNull UUID partyId
    ) throws SQLException {
        String sql = """
                SELECT party.leader_id, settings.join_policy
                FROM parties party
                JOIN party_group_settings settings USING (party_id)
                WHERE party_id = ?
                FOR UPDATE OF party
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, partyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new JoinableParty(
                        resultSet.getObject("leader_id", UUID.class),
                        GroupJoinPolicy.fromStoredValue(resultSet.getString("join_policy"))
                ));
            }
        }
    }

    private record JoinableParty(
            @NonNull UUID leaderId,
            @NonNull GroupJoinPolicy joinPolicy
    ) {
    }

    private static boolean isPartyMember(
            @NonNull Connection connection,
            @NonNull UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM party_members WHERE player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static int memberCount(
            @NonNull Connection connection,
            @NonNull UUID partyId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM party_members WHERE party_id = ?")) {
            statement.setObject(1, partyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
