package net.valoury.parties.proxy.storage;

import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class PartyMemberPostgresOperations {

    private PartyMemberPostgresOperations() {
    }

    static @NonNull PartyModeratorChangeOutcome updateModeratorStatus(
            @NonNull DataSource dataSource,
            @NonNull UUID partyId,
            @NonNull UUID leaderId,
            @NonNull UUID memberId,
            boolean moderator
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Boolean> currentModeratorStatus = lockAndFetchModeratorStatus(
                        connection,
                        partyId,
                        memberId
                );
                Optional<UUID> currentLeaderId = lockAndFetchLeader(connection, partyId);
                if (currentLeaderId.isEmpty()) {
                    connection.rollback();
                    return new PartyModeratorChangeOutcome.PartyNotFound();
                }
                if (!currentLeaderId.get().equals(leaderId)) {
                    connection.rollback();
                    return new PartyModeratorChangeOutcome.NotLeader();
                }
                if (currentLeaderId.get().equals(memberId)) {
                    connection.rollback();
                    return new PartyModeratorChangeOutcome.CannotChangeLeader();
                }

                if (currentModeratorStatus.isEmpty()) {
                    connection.rollback();
                    return new PartyModeratorChangeOutcome.MemberNotFound();
                }
                if (currentModeratorStatus.get() == moderator) {
                    connection.rollback();
                    return moderator
                            ? new PartyModeratorChangeOutcome.AlreadyModerator()
                            : new PartyModeratorChangeOutcome.NotModerator();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE party_members
                        SET is_moderator = ?
                        WHERE party_id = ? AND player_id = ?
                        """)) {
                    statement.setBoolean(1, moderator);
                    statement.setObject(2, partyId);
                    statement.setObject(3, memberId);
                    statement.executeUpdate();
                }

                connection.commit();
                return new PartyModeratorChangeOutcome.Changed();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to update party moderator status", exception);
        }
    }

    static @NonNull KickPartyMemberOutcome kickMember(
            @NonNull DataSource dataSource,
            @NonNull UUID partyId,
            @NonNull UUID requesterId,
            @NonNull UUID memberId
    ) {
        if (requesterId.equals(memberId)) {
            return new KickPartyMemberOutcome.CannotKickSelf();
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                acquirePartyJoinLock(connection, partyId);

                Map<UUID, Boolean> moderatorStatuses = lockAndFetchModeratorStatuses(
                        connection,
                        partyId,
                        requesterId,
                        memberId
                );
                Optional<UUID> leaderId = lockAndFetchLeader(connection, partyId);
                if (leaderId.isEmpty()) {
                    connection.rollback();
                    return new KickPartyMemberOutcome.PartyNotFound();
                }
                if (!moderatorStatuses.containsKey(requesterId)) {
                    connection.rollback();
                    return new KickPartyMemberOutcome.InsufficientRole();
                }
                if (!moderatorStatuses.containsKey(memberId)) {
                    connection.rollback();
                    return new KickPartyMemberOutcome.MemberNotFound();
                }
                if (leaderId.get().equals(memberId)) {
                    connection.rollback();
                    return new KickPartyMemberOutcome.CannotKickLeader();
                }

                boolean requesterIsLeader = leaderId.get().equals(requesterId);
                boolean requesterIsModerator = moderatorStatuses.get(requesterId);
                if (!requesterIsLeader && !requesterIsModerator) {
                    connection.rollback();
                    return new KickPartyMemberOutcome.InsufficientRole();
                }
                if (!requesterIsLeader && moderatorStatuses.get(memberId)) {
                    connection.rollback();
                    return new KickPartyMemberOutcome.CannotKickModerator();
                }

                int memberCount = memberCount(connection, partyId);

                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM party_members WHERE party_id = ? AND player_id = ?")) {
                    statement.setObject(1, partyId);
                    statement.setObject(2, memberId);
                    statement.executeUpdate();
                }
                deleteOutgoingInvitations(connection, partyId, memberId);
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM party_confirmations WHERE player_id = ?")) {
                    statement.setObject(1, memberId);
                    statement.executeUpdate();
                }
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
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM parties WHERE party_id = ?")) {
                        statement.setObject(1, partyId);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
                return new KickPartyMemberOutcome.Kicked();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to kick party member", exception);
        }
    }

    static @NonNull RevokePartyInvitationOutcome revokeInvitation(
            @NonNull DataSource dataSource,
            @NonNull UUID partyId,
            @NonNull UUID requesterId,
            @NonNull UUID targetId
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Boolean> requesterModeratorStatus = lockAndFetchModeratorStatus(
                        connection,
                        partyId,
                        requesterId
                );
                Optional<UUID> leaderId = lockAndFetchLeader(connection, partyId);
                if (leaderId.isEmpty()) {
                    connection.rollback();
                    return new RevokePartyInvitationOutcome.PartyNotFound();
                }
                boolean authorized = leaderId.get().equals(requesterId)
                        || requesterModeratorStatus.orElse(false);
                if (!authorized) {
                    connection.rollback();
                    return new RevokePartyInvitationOutcome.InsufficientRole();
                }

                int deletedInvitations;
                try (PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM party_invitations
                        WHERE target_id = ?
                          AND (
                              party_id = ?
                              OR (party_id IS NULL AND sender_id = ?)
                          )
                        """)) {
                    statement.setObject(1, targetId);
                    statement.setObject(2, partyId);
                    statement.setObject(3, requesterId);
                    deletedInvitations = statement.executeUpdate();
                }

                connection.commit();
                return deletedInvitations > 0
                        ? new RevokePartyInvitationOutcome.Revoked()
                        : new RevokePartyInvitationOutcome.InvitationNotFound();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to revoke party invitation", exception);
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

    static void deleteOutgoingInvitations(
            @NonNull Connection connection,
            @NonNull UUID partyId,
            @NonNull UUID memberId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM party_invitations
                WHERE party_id = ? AND sender_id = ?
                """)) {
            statement.setObject(1, partyId);
            statement.setObject(2, memberId);
            statement.executeUpdate();
        }
    }

    private static @NonNull Optional<Boolean> lockAndFetchModeratorStatus(
            @NonNull Connection connection,
            @NonNull UUID partyId,
            @NonNull UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT is_moderator
                FROM party_members
                WHERE party_id = ? AND player_id = ?
                FOR UPDATE
                """)) {
            statement.setObject(1, partyId);
            statement.setObject(2, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getBoolean("is_moderator"))
                        : Optional.empty();
            }
        }
    }

    private static @NonNull Map<UUID, Boolean> lockAndFetchModeratorStatuses(
            @NonNull Connection connection,
            @NonNull UUID partyId,
            @NonNull UUID firstPlayerId,
            @NonNull UUID secondPlayerId
    ) throws SQLException {
        String sql = """
                SELECT player_id, is_moderator
                FROM party_members
                WHERE party_id = ? AND player_id IN (?, ?)
                ORDER BY player_id
                FOR UPDATE
                """;
        Map<UUID, Boolean> moderatorStatuses = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, partyId);
            statement.setObject(2, firstPlayerId);
            statement.setObject(3, secondPlayerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    moderatorStatuses.put(
                            resultSet.getObject("player_id", UUID.class),
                            resultSet.getBoolean("is_moderator")
                    );
                }
            }
        }
        return moderatorStatuses;
    }
}
