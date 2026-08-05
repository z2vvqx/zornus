package net.valoury.parties.proxy.storage;

import net.valoury.parties.proxy.PartyProxyConstants;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class PartyInvitationPostgresOperations {

    private PartyInvitationPostgresOperations() {
    }

    static @NonNull SendInvitationOutcome sendInvitation(
            @NonNull DataSource dataSource,
            @NonNull Optional<UUID> expectedPartyId,
            @NonNull UUID senderId,
            @NonNull UUID targetId,
            boolean friend
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                acquirePlayerLocks(connection, senderId, targetId);

                Optional<PartyAuthority> senderAuthority = lockAndFetchPartyAuthority(
                        connection,
                        senderId
                );
                if (expectedPartyId.isPresent()
                        && senderAuthority.map(PartyAuthority::partyId)
                        .filter(expectedPartyId.get()::equals)
                        .isEmpty()) {
                    connection.rollback();
                    return new SendInvitationOutcome.PartyNoLongerExists();
                }
                if (senderAuthority.isPresent() && !senderAuthority.get().canManageInvitations()) {
                    connection.rollback();
                    return new SendInvitationOutcome.SenderInsufficientRole();
                }

                if (isPartyMember(connection, targetId)) {
                    connection.rollback();
                    return new SendInvitationOutcome.TargetAlreadyInParty();
                }
                Optional<UUID> partyId = senderAuthority.map(PartyAuthority::partyId);
                if (partyId.isPresent()
                        && memberCount(connection, partyId.get()) >= PartyProxyConstants.MAX_PARTY_SIZE) {
                    connection.rollback();
                    return new SendInvitationOutcome.PartyFull();
                }
                if (invitationExists(connection, partyId, senderId, targetId)) {
                    connection.rollback();
                    return new SendInvitationOutcome.AlreadyInvited();
                }

                String targetPrivacy = fetchInvitePrivacy(connection, targetId);
                if ("none".equals(targetPrivacy)
                        || ("friend".equals(targetPrivacy) && !friend)
                        || (!"all".equals(targetPrivacy) && !"friend".equals(targetPrivacy))) {
                    connection.rollback();
                    return new SendInvitationOutcome.InvitesDisabled(targetPrivacy);
                }

                if (hasActiveCooldown(connection, senderId, targetId)) {
                    connection.rollback();
                    return new SendInvitationOutcome.CooldownActive();
                }
                if (invitationCount(connection, senderId) >= PartyProxyConstants.MAX_PARTY_INVITATIONS) {
                    connection.rollback();
                    return new SendInvitationOutcome.SenderLimitReached();
                }
                if (invitationCount(connection, targetId) >= PartyProxyConstants.MAX_PARTY_INVITATIONS) {
                    connection.rollback();
                    return new SendInvitationOutcome.ReceiverLimitReached();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO party_invitations (party_id, sender_id, target_id, created_at)
                        VALUES (?, ?, ?, NOW())
                        """)) {
                    statement.setObject(1, partyId.orElse(null));
                    statement.setObject(2, senderId);
                    statement.setObject(3, targetId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO party_cooldowns (sender_id, receiver_id, timestamp)
                        VALUES (?, ?, NOW())
                        ON CONFLICT (sender_id, receiver_id)
                        DO UPDATE SET timestamp = EXCLUDED.timestamp
                        """)) {
                    statement.setObject(1, senderId);
                    statement.setObject(2, targetId);
                    statement.executeUpdate();
                }

                connection.commit();
                return new SendInvitationOutcome.Sent(partyId);
            } catch (SQLException exception) {
                connection.rollback();
                if ("23505".equals(exception.getSQLState())
                        || "40001".equals(exception.getSQLState())) {
                    return new SendInvitationOutcome.AlreadyInvited();
                }
                throw exception;
            } catch (RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to send party invitation", exception);
        }
    }

    static @NonNull JoinOutcome acceptInvitation(
            @NonNull DataSource dataSource,
            @NonNull UUID playerId,
            @NonNull UUID invitationSenderId
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                acquirePlayerLocks(connection, playerId, invitationSenderId);

                Optional<UUID> attachedPartyId = fetchAttachedPartyId(
                        connection,
                        invitationSenderId,
                        playerId
                );
                if (attachedPartyId.isPresent()) {
                    acquirePartyJoinLock(connection, attachedPartyId.get());
                }

                Optional<InvitationState> invitationOptional = lockAndFetchInvitation(
                        connection,
                        invitationSenderId,
                        playerId
                );
                if (invitationOptional.isEmpty()) {
                    connection.rollback();
                    return new JoinOutcome.InvitationNoLongerValid();
                }
                InvitationState invitation = invitationOptional.get();
                if (invitation.createdAt().plus(PartyProxyConstants.INVITATION_EXPIRY)
                        .isBefore(Instant.now())) {
                    deleteInvitation(connection, invitationSenderId, playerId);
                    connection.commit();
                    return new JoinOutcome.InvitationExpired();
                }
                if (isPartyMember(connection, playerId)) {
                    connection.rollback();
                    return new JoinOutcome.AlreadyMember();
                }

                Optional<UUID> effectivePartyId = invitation.partyId();
                if (effectivePartyId.isEmpty()) {
                    effectivePartyId = fetchPlayerPartyId(connection, invitationSenderId);
                }

                if (effectivePartyId.isPresent()) {
                    UUID partyId = effectivePartyId.get();
                    if (attachedPartyId.isEmpty()) {
                        acquirePartyJoinLock(connection, partyId);
                    }
                    Optional<PartyAuthority> senderAuthority = lockAndFetchPartyAuthority(
                            connection,
                            invitationSenderId
                    );
                    boolean invitationStillValid = senderAuthority
                            .filter(authority -> authority.partyId().equals(partyId))
                            .filter(PartyAuthority::canManageInvitations)
                            .isPresent();
                    if (!invitationStillValid) {
                        deleteInvitation(connection, invitationSenderId, playerId);
                        connection.commit();
                        return new JoinOutcome.InvitationNoLongerValid();
                    }
                    if (memberCount(connection, partyId) >= PartyProxyConstants.MAX_PARTY_SIZE) {
                        connection.rollback();
                        return new JoinOutcome.PartyFull();
                    }

                    insertMember(connection, partyId, playerId);
                    deleteInvitation(connection, invitationSenderId, playerId);
                    deleteStandaloneInvitationsSentBy(connection, playerId);
                    connection.commit();
                    return new JoinOutcome.Joined(partyId);
                }

                UUID partyId = UUID.randomUUID();
                createParty(connection, partyId, invitationSenderId, playerId);
                attachStandaloneInvitations(connection, partyId, invitationSenderId);
                deleteInvitation(connection, invitationSenderId, playerId);
                deleteStandaloneInvitationsSentBy(connection, playerId);
                connection.commit();
                return new JoinOutcome.Joined(partyId);
            } catch (SQLException exception) {
                connection.rollback();
                if ("23505".equals(exception.getSQLState())) {
                    return isPartyMember(connection, playerId)
                            ? new JoinOutcome.AlreadyMember()
                            : new JoinOutcome.InvitationNoLongerValid();
                }
                throw exception;
            } catch (RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to accept party invitation", exception);
        }
    }

    private static void createParty(
            @NonNull Connection connection,
            @NonNull UUID partyId,
            @NonNull UUID leaderId,
            @NonNull UUID joiningPlayerId
    ) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET CONSTRAINTS fk_leader_is_member DEFERRED");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO parties (party_id, leader_id, last_warp_time)
                VALUES (?, ?, NULL)
                """)) {
            statement.setObject(1, partyId);
            statement.setObject(2, leaderId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO party_members (party_id, player_id, is_moderator)
                VALUES (?, ?, FALSE), (?, ?, FALSE)
                """)) {
            statement.setObject(1, partyId);
            statement.setObject(2, leaderId);
            statement.setObject(3, partyId);
            statement.setObject(4, joiningPlayerId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO party_group_settings (party_id) VALUES (?)")) {
            statement.setObject(1, partyId);
            statement.executeUpdate();
        }
    }

    private static void insertMember(
            @NonNull Connection connection,
            @NonNull UUID partyId,
            @NonNull UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO party_members (party_id, player_id, is_moderator)
                VALUES (?, ?, FALSE)
                """)) {
            statement.setObject(1, partyId);
            statement.setObject(2, playerId);
            statement.executeUpdate();
        }
    }

    private static @NonNull Optional<InvitationState> lockAndFetchInvitation(
            @NonNull Connection connection,
            @NonNull UUID senderId,
            @NonNull UUID targetId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT party_id, created_at
                FROM party_invitations
                WHERE sender_id = ? AND target_id = ?
                FOR UPDATE
                """)) {
            statement.setObject(1, senderId);
            statement.setObject(2, targetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new InvitationState(
                        Optional.ofNullable(resultSet.getObject("party_id", UUID.class)),
                        resultSet.getTimestamp("created_at").toInstant()
                ));
            }
        }
    }

    private static @NonNull Optional<UUID> fetchAttachedPartyId(
            @NonNull Connection connection,
            @NonNull UUID senderId,
            @NonNull UUID targetId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT party_id
                FROM party_invitations
                WHERE sender_id = ? AND target_id = ?
                """)) {
            statement.setObject(1, senderId);
            statement.setObject(2, targetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.ofNullable(resultSet.getObject("party_id", UUID.class))
                        : Optional.empty();
            }
        }
    }

    private static @NonNull Optional<PartyAuthority> lockAndFetchPartyAuthority(
            @NonNull Connection connection,
            @NonNull UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT member.party_id, member.is_moderator, party.leader_id
                FROM party_members member
                JOIN parties party USING (party_id)
                WHERE member.player_id = ?
                FOR UPDATE OF member, party
                """)) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PartyAuthority(
                        resultSet.getObject("party_id", UUID.class),
                        resultSet.getObject("leader_id", UUID.class),
                        resultSet.getBoolean("is_moderator"),
                        playerId
                ));
            }
        }
    }

    private static @NonNull Optional<UUID> fetchPlayerPartyId(
            @NonNull Connection connection,
            @NonNull UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT party_id FROM party_members WHERE player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getObject("party_id", UUID.class))
                        : Optional.empty();
            }
        }
    }

    private static boolean isPartyMember(
            @NonNull Connection connection,
            @NonNull UUID playerId
    ) throws SQLException {
        return fetchPlayerPartyId(connection, playerId).isPresent();
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

    private static @NonNull String fetchInvitePrivacy(
            @NonNull Connection connection,
            @NonNull UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT invite_privacy FROM party_settings WHERE player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("invite_privacy") : "all";
            }
        }
    }

    private static boolean hasActiveCooldown(
            @NonNull Connection connection,
            @NonNull UUID senderId,
            @NonNull UUID targetId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT timestamp FROM party_cooldowns
                WHERE sender_id = ? AND receiver_id = ?
                """)) {
            statement.setObject(1, senderId);
            statement.setObject(2, targetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }
                Instant nextAllowed = resultSet.getTimestamp("timestamp").toInstant()
                        .plus(PartyProxyConstants.INVITATION_COOLDOWN);
                return Instant.now().isBefore(nextAllowed);
            }
        }
    }

    private static int invitationCount(
            @NonNull Connection connection,
            @NonNull UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT (SELECT COUNT(*) FROM party_invitations WHERE sender_id = ?)
                     + (SELECT COUNT(*) FROM party_invitations WHERE target_id = ?)
                """)) {
            statement.setObject(1, playerId);
            statement.setObject(2, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static boolean invitationExists(
            @NonNull Connection connection,
            @NonNull Optional<UUID> partyId,
            @NonNull UUID senderId,
            @NonNull UUID targetId
    ) throws SQLException {
        String sql = partyId.isPresent()
                ? "SELECT 1 FROM party_invitations WHERE party_id = ? AND target_id = ?"
                : "SELECT 1 FROM party_invitations WHERE party_id IS NULL AND sender_id = ? AND target_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, partyId.isPresent() ? partyId.get() : senderId);
            statement.setObject(2, targetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void deleteInvitation(
            @NonNull Connection connection,
            @NonNull UUID senderId,
            @NonNull UUID targetId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM party_invitations WHERE sender_id = ? AND target_id = ?")) {
            statement.setObject(1, senderId);
            statement.setObject(2, targetId);
            statement.executeUpdate();
        }
    }

    private static void attachStandaloneInvitations(
            @NonNull Connection connection,
            @NonNull UUID partyId,
            @NonNull UUID senderId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE party_invitations
                SET party_id = ?
                WHERE party_id IS NULL AND sender_id = ?
                """)) {
            statement.setObject(1, partyId);
            statement.setObject(2, senderId);
            statement.executeUpdate();
        }
    }

    private static void deleteStandaloneInvitationsSentBy(
            @NonNull Connection connection,
            @NonNull UUID senderId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM party_invitations
                WHERE party_id IS NULL AND sender_id = ?
                """)) {
            statement.setObject(1, senderId);
            statement.executeUpdate();
        }
    }

    private static void acquirePlayerLocks(
            @NonNull Connection connection,
            @NonNull UUID firstPlayerId,
            @NonNull UUID secondPlayerId
    ) throws SQLException {
        UUID smallerPlayerId = firstPlayerId.compareTo(secondPlayerId) < 0
                ? firstPlayerId
                : secondPlayerId;
        UUID largerPlayerId = smallerPlayerId.equals(firstPlayerId)
                ? secondPlayerId
                : firstPlayerId;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pg_advisory_xact_lock(hashtextextended(?, 0)),
                       pg_advisory_xact_lock(hashtextextended(?, 0))
                """)) {
            statement.setString(1, smallerPlayerId.toString());
            statement.setString(2, largerPlayerId.toString());
            statement.executeQuery();
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

    private record PartyAuthority(
            @NonNull UUID partyId,
            @NonNull UUID leaderId,
            boolean moderator,
            @NonNull UUID playerId
    ) {
        private boolean canManageInvitations() {
            return leaderId.equals(playerId) || moderator;
        }
    }

    private record InvitationState(
            @NonNull Optional<UUID> partyId,
            @NonNull Instant createdAt
    ) {
    }
}
