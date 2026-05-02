package com.zornus.parties.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.*;
import com.zornus.parties.proxy.model.result.PartyMembersResult;
import com.zornus.parties.proxy.model.result.PartyRequestsResult;
import com.zornus.parties.proxy.storage.*;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartyService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyService.class);

    private final @NonNull PartyStorage storage;
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull PartyNotificationService notificationService;
    private final @Nullable FriendService friendService;

    public PartyService(@NonNull PartyStorage storage, @NonNull ProxyServer proxyServer, @Nullable FriendService friendService) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.friendService = friendService;
        this.notificationService = new PartyNotificationService(proxyServer);
    }

    @Override
    public void close() {
        storage.close();
    }

    public @NonNull PartyNotificationService getNotificationService() {
        return notificationService;
    }

    public @NonNull CompletableFuture<PartyResult> createParty(@NonNull Player sender) {
        UUID senderId = sender.getUniqueId();
        Party party = new Party(senderId);
        return storage.createParty(party)
                .thenApply(outcome -> switch (outcome) {
                    case CreatePartyOutcome.Created created -> PartyResult.PARTY_CREATED;
                    case CreatePartyOutcome.AlreadyInParty alreadyInParty -> PartyResult.ALREADY_IN_PARTY;
                });
    }

    public @NonNull CompletableFuture<PartyResult> disbandParty(@NonNull Player sender, boolean isConfirming) {
        UUID senderId = sender.getUniqueId();
        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (!party.isLeader(senderId)) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NOT_LEADER);
                    }
                    return handleDisbandConfirmation(senderId, party, isConfirming);
                });
    }

    private @NonNull CompletableFuture<PartyResult> handleDisbandConfirmation(@NonNull UUID senderId, @NonNull Party party, boolean isConfirming) {
        if (!isConfirming) {
            return setupConfirmation(senderId, ConfirmationType.DISBAND_PARTY, null);
        }
        return confirmAndExecute(senderId, ConfirmationType.DISBAND_PARTY, null, () -> disbandPartyInternal(party, senderId));
    }

    private @NonNull CompletableFuture<PartyResult> setupConfirmation(@NonNull UUID playerId, @NonNull ConfirmationType type, @Nullable UUID targetId) {
        PendingConfirmation confirmation = new PendingConfirmation(playerId, type, targetId);
        return storage.setPendingConfirmation(confirmation)
                .thenCompose(outcome -> {
                    if (outcome instanceof ConfirmationOutcome.Set) {
                        return CompletableFuture.completedFuture(getRequiredResult(type));
                    }
                    ConfirmationOutcome.AlreadyExists alreadyExists = (ConfirmationOutcome.AlreadyExists) outcome;
                    PendingConfirmation existing = alreadyExists.existing();
                    if (existing.isExpired() || existing.type() != type) {
                        return storage.removePendingConfirmation(playerId)
                                .thenCompose(ignored -> storage.setPendingConfirmation(confirmation))
                                .thenApply(retryOutcome -> {
                                    if (retryOutcome instanceof ConfirmationOutcome.Set) {
                                        return getRequiredResult(type);
                                    }
                                    return PartyResult.NO_CONFIRMATION_PENDING;
                                });
                    }
                    if (existing.type() == type && (targetId == null || targetId.equals(existing.targetId()))) {
                        return CompletableFuture.completedFuture(getRequiredResult(type));
                    }
                    return CompletableFuture.completedFuture(PartyResult.NO_CONFIRMATION_PENDING);
                });
    }

    private @NonNull CompletableFuture<PartyResult> confirmAndExecute(@NonNull UUID playerId, @NonNull ConfirmationType expectedType,
                                                                       @Nullable UUID expectedTargetId, java.util.function.@NonNull Supplier<CompletableFuture<PartyResult>> onSuccess) {
        return storage.fetchPendingConfirmation(playerId)
                .thenCompose(existingOpt -> {
                    if (existingOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NO_CONFIRMATION_PENDING);
                    }
                    PendingConfirmation existing = existingOpt.get();
                    if (existing.isExpired() || existing.type() != expectedType) {
                        return storage.removePendingConfirmation(playerId)
                                .thenApply(ignored -> PartyResult.NO_CONFIRMATION_PENDING);
                    }
                    if (expectedTargetId != null && !expectedTargetId.equals(existing.targetId())) {
                        return CompletableFuture.completedFuture(PartyResult.NO_CONFIRMATION_PENDING);
                    }
                    return onSuccess.get();
                });
    }

    private @NonNull PartyResult getRequiredResult(@NonNull ConfirmationType type) {
        return switch (type) {
            case DISBAND_PARTY -> PartyResult.DISBAND_CONFIRMATION_REQUIRED;
            case TRANSFER_LEADERSHIP -> PartyResult.TRANSFER_CONFIRMATION_REQUIRED;
        };
    }

    private @NonNull CompletableFuture<PartyResult> disbandPartyInternal(@NonNull Party party, @NonNull UUID leaderId) {
        return storage.disbandParty(party.partyId(), leaderId)
                .thenApply(outcome -> switch (outcome) {
                    case DisbandPartyOutcome.Disbanded disbanded -> {
                        notificationService.notifyPartyDisbanded(party, leaderId);
                        yield PartyResult.PARTY_DISBANDED;
                    }
                    case DisbandPartyOutcome.PartyNotFound partyNotFound -> PartyResult.PARTY_NOT_FOUND;
                    case DisbandPartyOutcome.NotLeader notLeader -> PartyResult.NOT_LEADER;
                });
    }

    public @NonNull CompletableFuture<PartyResult> sendInvitation(@NonNull Player sender, @Nullable Player target) {
        if (target == null) {
            return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (senderId.equals(targetId)) {
            return CompletableFuture.completedFuture(PartyResult.CANNOT_INVITE_SELF);
        }

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (!party.isLeader(senderId)) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NOT_LEADER);
                    }
                    return executeSendInvitation(sender, target, party);
                });
    }

    private @NonNull CompletableFuture<PartyResult> executeSendInvitation(@NonNull Player sender, @NonNull Player target, @NonNull Party party) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (friendService != null) {
            return friendService.areFriends(senderId, targetId)
                    .thenCompose(isFriend -> executeStorageSendInvitation(sender, target, party, isFriend));
        } else {
            return executeStorageSendInvitation(sender, target, party, false);
        }
    }

    private @NonNull CompletableFuture<PartyResult> executeStorageSendInvitation(@NonNull Player sender, @NonNull Player target, @NonNull Party party, boolean isPreCheckedFriend) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        return storage.trySendInvitation(party.partyId(), senderId, targetId, isPreCheckedFriend)
                .thenApply(outcome -> switch (outcome) {
                    case SendInvitationOutcome.Sent sent -> {
                        notificationService.sendInviteReceived(target, sender, party);
                        notificationService.announceInviteSent(party, sender, target);
                        yield PartyResult.INVITATION_SENT;
                    }
                    case SendInvitationOutcome.TargetAlreadyInParty targetAlreadyInParty -> PartyResult.TARGET_ALREADY_IN_PARTY;
                    case SendInvitationOutcome.PartyFull partyFull -> PartyResult.PARTY_FULL;
                    case SendInvitationOutcome.CooldownActive cooldownActive -> PartyResult.INVITATION_COOLDOWN_ACTIVE;
                    case SendInvitationOutcome.SenderLimitReached senderLimitReached -> PartyResult.SENDER_INVITATION_LIMIT_REACHED;
                    case SendInvitationOutcome.ReceiverLimitReached receiverLimitReached -> PartyResult.RECEIVER_INVITATION_LIMIT_REACHED;
                    case SendInvitationOutcome.AlreadyInvited alreadyInvited -> PartyResult.ALREADY_INVITED;
                    case SendInvitationOutcome.InvitesDisabled invitesDisabled ->
                            "friend".equals(invitesDisabled.privacy()) ? PartyResult.INVITES_FRIENDS_ONLY : PartyResult.INVITES_DISABLED;
                    case SendInvitationOutcome.SenderNoLongerLeader senderNoLongerLeader -> PartyResult.NOT_LEADER;
                    case SendInvitationOutcome.PartyNoLongerExists partyNoLongerExists -> PartyResult.PARTY_NOT_FOUND;
                });
    }

    public @NonNull CompletableFuture<PartyResult> acceptInvitation(@NonNull Player sender, @Nullable Player target) {
        if (target == null) {
            return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        return storage.isInParty(senderId)
                .thenCompose(inParty -> {
                    if (inParty) {
                        return CompletableFuture.completedFuture(PartyResult.ALREADY_IN_PARTY);
                    }
                    return findAndAcceptInvitation(senderId, targetId);
                });
    }

    private @NonNull CompletableFuture<PartyResult> findAndAcceptInvitation(@NonNull UUID senderId, @NonNull UUID targetId) {
        return storage.findInvitationFromLeader(senderId, targetId)
                .thenCompose(invitationOptional -> {
                    if (invitationOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NO_INVITATION_FOUND);
                    }
                    PartyInvitation invitation = invitationOptional.get();
                    return addMemberToParty(senderId, invitation);
                });
    }

    private @NonNull CompletableFuture<PartyResult> addMemberToParty(@NonNull UUID playerId, @NonNull PartyInvitation invitation) {
        UUID partyId = invitation.partyId();
        return storage.acceptInvitationAndJoin(partyId, playerId, invitation.senderId())
                .thenCompose(outcome -> switch (outcome) {
                    case JoinOutcome.Joined joined ->
                            storage.fetchParty(partyId)
                                    .thenApply(partyOptional -> {
                                        partyOptional.ifPresent(party ->
                                                proxyServer.getPlayer(playerId).ifPresent(player ->
                                                        notificationService.notifyMemberJoined(party, player)));
                                        return PartyResult.JOINED_PARTY;
                                    });
                    case JoinOutcome.PartyFull partyFull -> CompletableFuture.completedFuture(PartyResult.PARTY_FULL);
                    case JoinOutcome.AlreadyMember alreadyMember -> CompletableFuture.completedFuture(PartyResult.ALREADY_IN_PARTY);
                    case JoinOutcome.InvitationExpired invitationExpired -> CompletableFuture.completedFuture(PartyResult.NO_INVITATION_FOUND);
                    case JoinOutcome.InvitationNoLongerValid invitationNoLongerValid -> CompletableFuture.completedFuture(PartyResult.NO_INVITATION_FOUND);
                });
    }

    public @NonNull CompletableFuture<PartyResult> rejectInvitation(@NonNull Player sender, @Nullable Player target) {
        if (target == null) {
            return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        return storage.findInvitationFromLeader(senderId, targetId)
                .thenCompose(invitationOptional -> {
                    if (invitationOptional.isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NO_INVITATION_FOUND);
                    }
                    PartyInvitation invitation = invitationOptional.get();
                    return storage.removePendingInvitation(invitation.partyId(), invitation.senderId(), senderId)
                            .thenApply(removed -> removed ? PartyResult.INVITATION_REJECTED : PartyResult.NO_INVITATION_FOUND);
                });
    }

    public @NonNull CompletableFuture<PartyResult> revokeInvitation(@NonNull Player sender, @Nullable Player target) {
        if (target == null) {
            return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (!party.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_LEADER);
                    }
                    return findAndRevokeInvitation(targetId, party.partyId());
                });
    }

    private @NonNull CompletableFuture<PartyResult> findAndRevokeInvitation(@NonNull UUID targetId, @NonNull UUID partyId) {
        return storage.findInvitationForParty(targetId, partyId)
                .thenCompose(invitationOptional -> {
                    if (invitationOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NO_INVITATION_FOUND);
                    }
                    PartyInvitation invitation = invitationOptional.get();
                    return storage.removePendingInvitation(invitation.partyId(), invitation.senderId(), targetId)
                            .thenApply(removed -> removed ? PartyResult.INVITATION_REVOKED : PartyResult.NO_INVITATION_FOUND);
                });
    }

    public @NonNull CompletableFuture<PartyRequestsResult> getRequestsList(@NonNull UUID playerId, @NonNull String type, int page) {
        CompletableFuture<List<PartyInvitation>> invitationsFuture;
        if ("incoming".equalsIgnoreCase(type)) {
            invitationsFuture = storage.fetchIncomingInvitations(playerId);
        } else if ("outgoing".equalsIgnoreCase(type)) {
            invitationsFuture = storage.fetchOutgoingInvitations(playerId);
        } else {
            return CompletableFuture.completedFuture(
                    new PartyRequestsResult(PartyResult.INVALID_REQUEST_TYPE, PaginationResult.invalidPage(1)));
        }

        return invitationsFuture.thenApply(invitations -> {
            if (invitations.isEmpty()) {
                return new PartyRequestsResult(PartyResult.LIST_EMPTY, PaginationResult.invalidPage(1));
            }

            PaginationResult<PartyInvitation> pagination = PaginationResult.paginate(invitations, page, SharedConstants.ENTRIES_PER_PAGE);
            if (!pagination.isValidPage()) {
                return new PartyRequestsResult(PartyResult.INVALID_PAGE, pagination);
            }
            return new PartyRequestsResult(PartyResult.SUCCESS, pagination);
        });
    }

    public @NonNull CompletableFuture<PartyResult> leaveParty(@NonNull Player sender) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    return removePlayerFromParty(senderId, party, true)
                            .thenApply(result -> {
                                if (result == PartyResult.LEFT_PARTY || result == PartyResult.LEFT_PARTY_DISBANDED) {
                                    notificationService.notifyMemberLeft(party, senderId);
                                }
                                return result;
                            });
                });
    }

    public @NonNull CompletableFuture<PartyResult> kickMember(@NonNull Player sender, @Nullable Player target, @Nullable String reason) {
        if (target == null) {
            return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (senderId.equals(targetId)) {
            return CompletableFuture.completedFuture(PartyResult.CANNOT_KICK_SELF);
        }

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (!party.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_LEADER);
                    }
                    if (!party.isMember(targetId)) {
                        return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_IN_PARTY);
                    }
                    return removePlayerFromParty(targetId, party, false)
                            .thenCompose(result -> {
                                if (result == PartyResult.LEFT_PARTY) {
                                    return storage.fetchParty(party.partyId())
                                            .thenApply(updatedPartyOptional -> {
                                                updatedPartyOptional.ifPresent(updatedParty ->
                                                        notificationService.notifyMemberKicked(updatedParty, target, reason));
                                                return PartyResult.MEMBER_KICKED;
                                            });
                                }
                                return CompletableFuture.completedFuture(result);
                            });
                });
    }

    public @NonNull CompletableFuture<PartyMembersResult> getPartyMembers(@NonNull Player sender, int page) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenApply(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return new PartyMembersResult(PartyResult.NOT_IN_PARTY, PaginationResult.invalidPage(1));
                    }
                    Party party = partyOptional.get();
                    List<UUID> members = new ArrayList<>(party.getMemberIds());
                    // New sort: leader first, then UUID natural ordering
                    members.sort((a, b) -> {
                        if (party.isLeader(a)) return -1;
                        if (party.isLeader(b)) return 1;
                        return a.compareTo(b);
                    });

                    if (members.isEmpty()) {
                        return new PartyMembersResult(PartyResult.LIST_EMPTY, PaginationResult.invalidPage(1));
                    }

                    PaginationResult<UUID> pagination = PaginationResult.paginate(members, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new PartyMembersResult(PartyResult.INVALID_PAGE, pagination);
                    }
                    return new PartyMembersResult(PartyResult.SUCCESS, pagination);
                });
    }

    public @NonNull CompletableFuture<PartyResult> sendPartyChat(@NonNull Player sender, @NonNull String message) {
        if (message.length() > PartyProxyConstants.MAX_MESSAGE_LENGTH) {
            return CompletableFuture.completedFuture(PartyResult.MESSAGE_TOO_LONG);
        }

        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    Set<UUID> memberIds = party.getMemberIds();

                    LOGGER.debug("Party chat from {} in party {} (leader: {}, members: {}, member count: {})",
                            senderId, party.partyId(), party.leaderId(), memberIds, memberIds.size());

                    if (!memberIds.contains(senderId)) {
                        LOGGER.warn("Sender {} not in party {} member list, but was able to getPlayerParty. Members: {}",
                                senderId, party.partyId(), memberIds);
                    }

                    return storage.fetchSettingsForMembers(memberIds)
                            .thenApply(memberSettingsMap -> {
                                PartySettings senderSettings = memberSettingsMap.getOrDefault(senderId, new PartySettings(senderId));

                                LOGGER.debug("Party chat settings for {}: allowChat={}, in DB={}",
                                        senderId, senderSettings.allowChat(), memberSettingsMap.containsKey(senderId));

                                // Log all member settings for debugging
                                for (Map.Entry<UUID, PartySettings> entry : memberSettingsMap.entrySet()) {
                                    LOGGER.debug("  Member {}: allowChat={}", entry.getKey(), entry.getValue().allowChat());
                                }

                                if (!senderSettings.allowChat()) {
                                    LOGGER.warn("Party chat blocked for {}: allowChat is false in settings", senderId);
                                    return PartyResult.CHAT_DISABLED;
                                }
                                notificationService.sendPartyChatFiltered(party, sender, message, memberSettingsMap);
                                return PartyResult.CHAT_SENT;
                            });
                });
    }

    public @NonNull CompletableFuture<Void> handlePlayerDisconnect(@NonNull UUID playerId, @NonNull String username) {
        return storage.getPlayerParty(playerId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    Party party = partyOptional.get();
                    boolean wasLeader = party.isLeader(playerId);
                    return removePlayerFromParty(playerId, party, true)
                            .thenCompose(result -> {
                                if (result != PartyResult.LEFT_PARTY && result != PartyResult.LEFT_PARTY_DISBANDED) {
                                    return CompletableFuture.<Void>completedFuture(null);
                                }
                                // Fetch updated party to check if it still exists and get new leader
                                return storage.fetchParty(party.partyId())
                                        .thenAccept(updatedPartyOptional -> {
                                            if (updatedPartyOptional.isEmpty()) {
                                                // Party was disbanded
                                                return;
                                            }
                                            Party updatedParty = updatedPartyOptional.get();
                                            if (wasLeader) {
                                                notificationService.notifyLeaderDisconnected(updatedParty, playerId, username);
                                            } else {
                                                notificationService.notifyMemberDisconnected(updatedParty, playerId, username);
                                            }
                                        });
                            });
                });
    }

    private @NonNull CompletableFuture<PartyResult> removePlayerFromParty(@NonNull UUID memberId, @NonNull Party party, boolean isLeaving) {
        return storage.removeMember(party.partyId(), memberId)
                .thenApply(outcome -> switch (outcome) {
                    case RemoveMemberOutcome.MemberRemoved memberRemoved -> PartyResult.LEFT_PARTY;
                    case RemoveMemberOutcome.LeaderTransferred leaderTransferred -> {
                        proxyServer.getPlayer(leaderTransferred.newLeaderId()).ifPresent(newLeader ->
                                notificationService.notifyLeadershipTransferred(party, memberId, newLeader));
                        yield PartyResult.LEFT_PARTY;
                    }
                    case RemoveMemberOutcome.PartyDisbanded partyDisbanded ->
                            isLeaving ? PartyResult.LEFT_PARTY_DISBANDED : PartyResult.LEFT_PARTY;
                    case RemoveMemberOutcome.MemberNotFound memberNotFound -> PartyResult.PLAYER_NOT_IN_PARTY;
                    case RemoveMemberOutcome.PartyNotFound partyNotFound -> PartyResult.PARTY_NOT_FOUND;
                });
    }

    public @NonNull CompletableFuture<PartyResult> transferLeadership(@NonNull Player sender, @Nullable Player target, boolean isConfirming) {
        if (target == null) {
            return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (senderId.equals(targetId)) {
            return CompletableFuture.completedFuture(PartyResult.CANNOT_TRANSFER_TO_SELF);
        }

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (!party.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_LEADER);
                    }
                    if (!party.isMember(targetId)) {
                        return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_IN_PARTY);
                    }
                    return handleTransferConfirmation(senderId, targetId, party, isConfirming);
                });
    }

    private @NonNull CompletableFuture<PartyResult> handleTransferConfirmation(@NonNull UUID senderId, @NonNull UUID targetId, @NonNull Party party, boolean isConfirming) {
        if (!isConfirming) {
            return setupConfirmation(senderId, ConfirmationType.TRANSFER_LEADERSHIP, targetId);
        }
        return confirmAndExecute(senderId, ConfirmationType.TRANSFER_LEADERSHIP, targetId,
                () -> executeTransferLeadership(senderId, targetId, party));
    }

    private @NonNull CompletableFuture<PartyResult> executeTransferLeadership(@NonNull UUID senderId, @NonNull UUID targetId, @NonNull Party party) {
        if (!party.isMember(targetId)) {
            return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_IN_PARTY);
        }
        return storage.transferLeadership(party.partyId(), targetId, senderId)
                .thenApply(outcome -> switch (outcome) {
                    case TransferLeadershipOutcome.Transferred transferred -> {
                        proxyServer.getPlayer(targetId).ifPresent(newLeader ->
                                notificationService.notifyLeadershipTransferred(party, senderId, newLeader));
                        yield PartyResult.LEADERSHIP_TRANSFERRED;
                    }
                    case TransferLeadershipOutcome.PartyNotFound partyNotFound -> PartyResult.PARTY_NOT_FOUND;
                    case TransferLeadershipOutcome.TargetNotMember targetNotMember -> PartyResult.PLAYER_NOT_IN_PARTY;
                });
    }

    public @NonNull CompletableFuture<PartyResult> warpParty(@NonNull Player sender) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (!party.isLeader(senderId)) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NOT_LEADER);
                    }
                    return checkAndExecuteWarp(party, sender);
                });
    }

    private @NonNull CompletableFuture<PartyResult> checkAndExecuteWarp(@NonNull Party party, @NonNull Player sender) {
        Instant now = Instant.now();
        return storage.checkAndUpdateLastWarpTime(party.partyId(), now, PartyProxyConstants.WARP_COOLDOWN)
                .thenCompose(outcome -> switch (outcome) {
                    case WarpOutcome.Allowed allowed -> executePartyWarp(party, sender);
                    case WarpOutcome.OnCooldown onCooldown ->
                            CompletableFuture.<PartyResult>completedFuture(PartyResult.WARP_ON_COOLDOWN);
                    case WarpOutcome.PartyNotFound partyNotFound ->
                            CompletableFuture.<PartyResult>completedFuture(PartyResult.PARTY_NOT_FOUND);
                });
    }

    private @NonNull CompletableFuture<PartyResult> executePartyWarp(@NonNull Party party, @NonNull Player sender) {
        Optional<ServerConnection> senderServerOptional = sender.getCurrentServer();
        if (senderServerOptional.isEmpty()) {
            return CompletableFuture.completedFuture(PartyResult.WARP_FAILED);
        }
        RegisteredServer targetServer = senderServerOptional.get().getServer();

        return storage.fetchSettingsForMembers(party.getMemberIds())
                .thenCompose(settingsMap -> {
                    List<CompletableFuture<Void>> warpFutures = new ArrayList<>();

                    for (UUID memberId : party.getMemberIds()) {
                        if (!party.isLeader(memberId)) {
                            PartySettings settings = settingsMap.getOrDefault(memberId, new PartySettings(memberId));
                            if (!settings.allowWarp()) {
                                continue;
                            }

                            Optional<Player> memberOptional = proxyServer.getPlayer(memberId);
                            if (memberOptional.isEmpty()) {
                                continue;
                            }
                            Player member = memberOptional.get();
                            Optional<ServerConnection> memberServerOptional = member.getCurrentServer();
                            if (memberServerOptional.isEmpty() || memberServerOptional.get().getServer().equals(targetServer)) {
                                continue;
                            }

                            CompletableFuture<Void> warpFuture = member.createConnectionRequest(targetServer)
                                    .connect()
                                    .thenAccept(result -> notificationService.notifyMemberWarped(member, sender))
                                    .exceptionally(throwable -> null);
                            warpFutures.add(warpFuture);
                        }
                    }

                    return CompletableFuture.allOf(warpFutures.toArray(new CompletableFuture[0]))
                            .thenApply(ignored -> PartyResult.PARTY_WARPED);
                });
    }

    public @NonNull CompletableFuture<PartyResult> jumpToLeader(@NonNull Player sender) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (party.isLeader(senderId)) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.CANNOT_JUMP_AS_LEADER);
                    }

                    UUID leaderId = party.leaderId();
                    Optional<Player> leaderOptional = proxyServer.getPlayer(leaderId);
                    if (leaderOptional.isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.LEADER_NOT_ONLINE);
                    }
                    Player leader = leaderOptional.get();

                    if (leader.getCurrentServer().isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.LEADER_NO_INSTANCE);
                    }

                    Optional<RegisteredServer> senderServer = sender.getCurrentServer().map(ServerConnection::getServer);
                    Optional<RegisteredServer> leaderServer = leader.getCurrentServer().map(ServerConnection::getServer);
                    if (senderServer.isPresent() && leaderServer.isPresent()
                            && senderServer.get().equals(leaderServer.get())) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.ALREADY_WITH_LEADER);
                    }

                    return sender.createConnectionRequest(leader.getCurrentServer().get().getServer())
                            .connect()
                            .thenApply(result -> PartyResult.JUMPED_TO_LEADER)
                            .exceptionally(throwable -> PartyResult.WARP_FAILED);
                });
    }

    public @NonNull CompletableFuture<PartyResult> updateBooleanSetting(@NonNull UUID playerId, @NonNull String settingName, boolean value) {
        // Validate setting name first
        if (!settingName.equals("allow_chat") && !settingName.equals("allow_warp")) {
            return CompletableFuture.completedFuture(PartyResult.INVALID_SETTING);
        }

        // Use atomic column-specific update to avoid read-modify-write race
        CompletableFuture<Void> updateFuture = switch (settingName) {
            case "allow_chat" -> storage.updateAllowChat(playerId, value);
            case "allow_warp" -> storage.updateAllowWarp(playerId, value);
            default -> throw new IllegalArgumentException("Unknown setting: " + settingName);
        };

        return updateFuture
                .thenApply(ignored -> PartyResult.SETTING_UPDATED)
                .exceptionally(throwable -> PartyResult.INVALID_SETTING);
    }

    public @NonNull CompletableFuture<PartyResult> updateInvitePrivacy(@NonNull UUID playerId, @NonNull String value) {
        // Validate value first
        if (!value.equals("all") && !value.equals("friend") && !value.equals("none")) {
            return CompletableFuture.completedFuture(PartyResult.INVALID_SETTING);
        }

        // Use atomic column-specific update to avoid read-modify-write race
        return storage.updateInvitePrivacy(playerId, value)
                .thenApply(ignored -> PartyResult.SETTING_UPDATED)
                .exceptionally(throwable -> PartyResult.INVALID_SETTING);
    }

    public @NonNull CompletableFuture<PartySettings> getSettings(@NonNull UUID playerId) {
        return storage.fetchSettings(playerId)
                .thenApply(settingsOptional -> settingsOptional.orElse(new PartySettings(playerId)));
    }

    public @NonNull CompletableFuture<Boolean> isInParty(@NonNull UUID playerId) {
        return storage.isInParty(playerId);
    }

    public @NonNull CompletableFuture<Optional<Party>> getPlayerParty(@NonNull UUID playerId) {
        return storage.getPlayerParty(playerId);
    }

    public @NonNull Optional<Player> findPlayerByUsername(@NonNull String username) {
        return proxyServer.getPlayer(username);
    }

    public @NonNull CompletableFuture<Duration> getRemainingInvitationCooldown(@NonNull UUID senderId, @NonNull UUID receiverId) {
        return storage.fetchInvitationCooldown(senderId, receiverId)
                .thenApply(lastOptional -> {
                    if (lastOptional.isEmpty()) {
                        return Duration.ZERO;
                    }
                    Instant cooldownEnd = lastOptional.get().plus(PartyProxyConstants.INVITATION_COOLDOWN);
                    Instant now = Instant.now();
                    return now.isAfter(cooldownEnd) ? Duration.ZERO : Duration.between(now, cooldownEnd);
                });
    }

    public CompletableFuture<Void> cleanupExpiredInvitations() {
        return storage.cleanupExpiredInvitations(Instant.now(), PartyProxyConstants.INVITATION_EXPIRY);
    }

    public CompletableFuture<Void> cleanupExpiredConfirmations() {
        return storage.cleanupExpiredConfirmations(Instant.now(), PartyProxyConstants.CONFIRMATION_EXPIRY);
    }

    public CompletableFuture<Void> cleanupExpiredCooldowns() {
        return storage.cleanupExpiredCooldowns(Instant.now(), PartyProxyConstants.INVITATION_COOLDOWN.multipliedBy(2));
    }

    public CompletableFuture<Void> cleanupOrphanedSettings() {
        return storage.cleanupOrphanedSettings();
    }
}
