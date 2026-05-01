package com.zornus.parties.proxy.storage;

import com.zornus.parties.proxy.model.Party;
import com.zornus.parties.proxy.model.PartyInvitation;
import com.zornus.parties.proxy.model.PartySettings;
import com.zornus.parties.proxy.model.PendingConfirmation;
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
import java.util.function.Predicate;

public interface PartyStorage {

    // Compound operations
    CompletableFuture<CreatePartyOutcome> createParty(@NonNull Party party);
    CompletableFuture<DisbandPartyOutcome> disbandParty(@NonNull UUID partyId, @NonNull UUID leaderId);
    CompletableFuture<RemoveMemberOutcome> removeMember(@NonNull UUID partyId, @NonNull UUID memberId,
                                                         @Nullable UUID newLeaderId, @Nullable String newLeaderName);
    CompletableFuture<JoinOutcome> acceptInvitationAndJoin(@NonNull UUID partyId, @NonNull UUID playerId,
                                                            @NonNull String playerName, @NonNull UUID invitationSenderId);
    CompletableFuture<TransferLeadershipOutcome> transferLeadership(@NonNull UUID partyId, @NonNull UUID newLeaderId,
                                                                       @NonNull String newLeaderName, @NonNull UUID confirmedByPlayerId);
    CompletableFuture<WarpOutcome> checkAndUpdateLastWarpTime(@NonNull UUID partyId, @NonNull Instant now,
                                                                 @NonNull Duration cooldown);
    CompletableFuture<Map<UUID, PartySettings>> fetchSettingsForMembers(@NonNull Collection<UUID> memberIds);

    CompletableFuture<SendInvitationOutcome> trySendInvitation(@NonNull UUID partyId, @NonNull String partyName,
                                                                @NonNull UUID senderId, @NonNull String senderName,
                                                                @NonNull UUID targetId, @NonNull String targetName,
                                                                @Nullable Predicate<UUID> isFriendChecker);

    // Single-query operations
    CompletableFuture<Optional<Party>> fetchParty(@NonNull UUID partyId);
    CompletableFuture<Boolean> isInParty(@NonNull UUID playerId);
    CompletableFuture<Optional<Party>> getPlayerParty(@NonNull UUID playerId);

    CompletableFuture<Boolean> addPendingInvitation(@NonNull PartyInvitation invitation);
    CompletableFuture<Boolean> removePendingInvitation(@NonNull UUID partyId, @NonNull UUID senderId, @NonNull UUID targetId);
    CompletableFuture<Optional<PartyInvitation>> fetchInvitation(@NonNull UUID partyId, @NonNull UUID senderId, @NonNull UUID targetId);
    CompletableFuture<List<PartyInvitation>> fetchIncomingInvitations(@NonNull UUID playerId);
    CompletableFuture<List<PartyInvitation>> fetchOutgoingInvitations(@NonNull UUID playerId);
    CompletableFuture<List<PartyInvitation>> fetchPartyOutgoingInvitations(@NonNull UUID partyId);
    CompletableFuture<Optional<PartyInvitation>> findInvitationFromLeader(@NonNull UUID inviteeId, @NonNull UUID leaderId);
    CompletableFuture<Optional<PartyInvitation>> findInvitationForParty(@NonNull UUID inviteeId, @NonNull UUID partyId);
    CompletableFuture<Boolean> hasInvitation(@NonNull UUID inviteeId, @NonNull UUID partyId);
    CompletableFuture<Integer> countIncomingInvitations(@NonNull UUID playerId);
    CompletableFuture<Integer> countOutgoingInvitations(@NonNull UUID playerId);

    CompletableFuture<Void> setPendingConfirmation(@NonNull PendingConfirmation confirmation);
    CompletableFuture<Void> removePendingConfirmation(@NonNull UUID playerId);
    CompletableFuture<Optional<PendingConfirmation>> fetchPendingConfirmation(@NonNull UUID playerId);

    CompletableFuture<Optional<PartySettings>> fetchSettings(@NonNull UUID playerId);
    CompletableFuture<Void> saveSettings(@NonNull UUID playerId, @NonNull PartySettings settings);

    CompletableFuture<Boolean> recordInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId);
    CompletableFuture<Optional<Instant>> fetchInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId);

    CompletableFuture<Void> cleanupExpiredInvitations(@NonNull Duration expiry);
    CompletableFuture<Void> cleanupExpiredConfirmations(@NonNull Duration expiry);
    CompletableFuture<Void> cleanupExpiredCooldowns(@NonNull Duration expiry);

    void close();
}
