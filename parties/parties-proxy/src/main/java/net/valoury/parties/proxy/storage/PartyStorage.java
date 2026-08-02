package net.valoury.parties.proxy.storage;

import net.valoury.parties.proxy.model.Party;
import net.valoury.parties.proxy.model.PartyGroupSettings;
import net.valoury.parties.proxy.model.PartyInvitation;
import net.valoury.parties.proxy.model.PartySettings;
import net.valoury.parties.proxy.model.PendingConfirmation;
import net.valoury.shared.model.GroupJoinPolicy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PartyStorage {

    // Compound operations
    CompletableFuture<DisbandPartyOutcome> disbandParty(@NonNull UUID partyId, @NonNull UUID leaderId);

    CompletableFuture<RemoveMemberOutcome> removeMember(@NonNull UUID partyId, @NonNull UUID memberId);

    CompletableFuture<KickPartyMemberOutcome> tryKickMember(
            @NonNull UUID partyId,
            @NonNull UUID requesterId,
            @NonNull UUID memberId
    );

    CompletableFuture<JoinOutcome> acceptInvitationAndJoin(
            @NonNull UUID playerId,
            @NonNull UUID invitationSenderId
    );

    CompletableFuture<JoinPublicPartyOutcome> tryJoinPublicParty(
            @NonNull UUID partyId,
            @NonNull UUID expectedLeaderId,
            @NonNull UUID playerId
    );

    CompletableFuture<TransferLeadershipOutcome> transferLeadership(@NonNull UUID partyId, @NonNull UUID newLeaderId, @NonNull UUID confirmedByPlayerId);

    CompletableFuture<PartyModeratorChangeOutcome> updateModeratorStatus(
            @NonNull UUID partyId,
            @NonNull UUID leaderId,
            @NonNull UUID memberId,
            boolean moderator
    );

    CompletableFuture<WarpOutcome> checkAndUpdateLastWarpTime(
            @NonNull UUID partyId,
            @NonNull UUID leaderId,
            @NonNull Instant now,
            @NonNull Duration cooldown
    );

    CompletableFuture<Map<UUID, PartySettings>> fetchSettingsForMembers(@NonNull Collection<UUID> memberIds);

    CompletableFuture<SendInvitationOutcome> trySendInvitation(
            @NonNull Optional<UUID> expectedPartyId,
            @NonNull UUID senderId,
            @NonNull UUID targetId,
            boolean isFriend
    );

    CompletableFuture<RevokePartyInvitationOutcome> tryRevokeInvitation(
            @NonNull Optional<UUID> partyId,
            @NonNull UUID requesterId,
            @NonNull UUID targetId
    );

    CompletableFuture<ConfirmationOutcome> setPendingConfirmation(@NonNull PendingConfirmation confirmation);

    // Single-query operations
    CompletableFuture<Optional<Party>> fetchParty(@NonNull UUID partyId);

    CompletableFuture<Boolean> isInParty(@NonNull UUID playerId);

    CompletableFuture<Optional<Party>> getPlayerParty(@NonNull UUID playerId);

    CompletableFuture<Boolean> removePendingInvitation(
            @NonNull UUID senderId,
            @NonNull UUID targetId
    );

    CompletableFuture<List<PartyInvitation>> fetchIncomingInvitations(@NonNull UUID playerId);

    CompletableFuture<List<PartyInvitation>> fetchOutgoingInvitations(@NonNull UUID playerId);

    CompletableFuture<Optional<PartyInvitation>> findInvitationFromSender(@NonNull UUID inviteeId, @NonNull UUID senderId);

    CompletableFuture<Integer> countIncomingInvitations(@NonNull UUID playerId);

    CompletableFuture<Integer> countOutgoingInvitations(@NonNull UUID playerId);

    CompletableFuture<Void> removePendingConfirmation(@NonNull UUID playerId);

    CompletableFuture<Optional<PendingConfirmation>> fetchPendingConfirmation(@NonNull UUID playerId);

    CompletableFuture<Optional<PartySettings>> fetchSettings(@NonNull UUID playerId);

    CompletableFuture<Optional<PartyGroupSettings>> fetchGroupSettings(@NonNull UUID partyId);

    CompletableFuture<Void> updateAllowChat(@NonNull UUID playerId, boolean allowChat);

    CompletableFuture<Void> updateAllowWarp(@NonNull UUID playerId, boolean allowWarp);

    CompletableFuture<Void> updateAutoWarp(@NonNull UUID playerId, boolean autoWarp);

    CompletableFuture<Void> updateInvitePrivacy(@NonNull UUID playerId, @NonNull String invitePrivacy);

    CompletableFuture<UpdatePartyJoinPolicyOutcome> updateJoinPolicy(
            @NonNull UUID partyId,
            @NonNull UUID requesterId,
            @NonNull GroupJoinPolicy joinPolicy
    );

    CompletableFuture<Boolean> recordInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId, @NonNull Instant now);

    CompletableFuture<Optional<Instant>> fetchInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId);

    CompletableFuture<Void> cleanupExpiredInvitations(@NonNull Instant now, @NonNull Duration expiry);

    CompletableFuture<Void> cleanupExpiredConfirmations(@NonNull Instant now, @NonNull Duration expiry);

    CompletableFuture<Void> cleanupExpiredCooldowns(@NonNull Instant now, @NonNull Duration expiry);

    void close();
}
