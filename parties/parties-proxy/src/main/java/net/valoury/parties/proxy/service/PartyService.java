package net.valoury.parties.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.valoury.friends.api.FriendshipService;
import net.luckperms.api.LuckPerms;
import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.*;
import net.valoury.parties.proxy.model.result.PartyMembersResult;
import net.valoury.parties.proxy.model.result.PartyRequestsResult;
import net.valoury.parties.proxy.model.result.PartyResults;
import net.valoury.parties.proxy.storage.*;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.GroupJoinPolicy;
import net.valoury.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class PartyService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyService.class);

    private final @NonNull PartyStorage storage;
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull PartyNotificationService notificationService;
    private final @NonNull FriendshipService friendshipService;

    public PartyService(
            @NonNull PartyStorage storage,
            @NonNull ProxyServer proxyServer,
            @NonNull FriendshipService friendshipService,
            @NonNull LuckPerms luckPerms
    ) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.friendshipService = friendshipService;
        this.notificationService = new PartyNotificationService(proxyServer, luckPerms);
    }

    @Override
    public void close() {
        storage.close();
    }

    public @NonNull PartyNotificationService getNotificationService() {
        return notificationService;
    }

    public @NonNull CompletableFuture<PartyResults.Disband> disbandParty(
            @NonNull Player sender,
            boolean isConfirming
    ) {
        return disbandPartyLegacy(sender, isConfirming).thenApply(PartyResults.Disband::from);
    }

    private @NonNull CompletableFuture<PartyResult> disbandPartyLegacy(
            @NonNull Player sender,
            boolean isConfirming
    ) {
        UUID senderId = sender.getUniqueId();
        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (!party.isLeader(senderId)) {
                        return CompletableFuture.<PartyResult>completedFuture(
                                PartyResult.NOT_LEADER);
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
                    boolean paramsMismatch = targetId != null && !targetId.equals(existing.targetId());
                    if (existing.isExpired() || existing.type() != type || paramsMismatch) {
                        return storage.removePendingConfirmation(playerId)
                                .thenCompose(ignored -> storage.setPendingConfirmation(confirmation))
                                .thenApply(retryOutcome -> {
                                    if (retryOutcome instanceof ConfirmationOutcome.Set) {
                                        return getRequiredResult(type);
                                    }
                                    return PartyResult.NO_CONFIRMATION_PENDING;
                                });
                    }
                    // Exact match â€” confirm the action
                    return CompletableFuture.completedFuture(getRequiredResult(type));
                });
    }

    private @NonNull CompletableFuture<PartyResult> confirmAndExecute(@NonNull UUID playerId, @NonNull ConfirmationType expectedType,
                                                                      @Nullable UUID expectedTargetId, @NonNull Supplier<CompletableFuture<PartyResult>> onSuccess) {
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
                    return storage.removePendingConfirmation(playerId)
                            .thenCompose(ignored -> onSuccess.get());
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

    public @NonNull CompletableFuture<PartyResults.SendInvitation> sendInvitation(
            @NonNull Player sender,
            @Nullable Player target
    ) {
        return sendInvitationLegacy(sender, target).thenApply(PartyResults.SendInvitation::from);
    }

    private @NonNull CompletableFuture<PartyResult> sendInvitationLegacy(
            @NonNull Player sender,
            @Nullable Player target
    ) {
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
                    if (partyOptional.isPresent()
                            && !partyOptional.get().canManageMembers(senderId)) {
                        return CompletableFuture.<PartyResult>completedFuture(
                                PartyResult.INSUFFICIENT_ROLE);
                    }
                    return executeSendInvitation(sender, target, partyOptional);
                });
    }

    private @NonNull CompletableFuture<PartyResult> executeSendInvitation(
            @NonNull Player sender,
            @NonNull Player target,
            @NonNull Optional<Party> party
    ) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        return friendshipService.areFriends(senderId, targetId)
                .thenCompose(areFriends -> executeStorageSendInvitation(sender, target, party, areFriends));
    }

    private @NonNull CompletableFuture<PartyResult> executeStorageSendInvitation(
            @NonNull Player sender,
            @NonNull Player target,
            @NonNull Optional<Party> party,
            boolean isPreCheckedFriend
    ) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        return storage.trySendInvitation(
                        party.map(Party::partyId),
                        senderId,
                        targetId,
                        isPreCheckedFriend
                )
                .thenCompose(outcome -> switch (outcome) {
                    case SendInvitationOutcome.Sent sent -> {
                        notificationService.sendInviteReceived(target, sender);
                        if (sent.partyId().isEmpty()) {
                            yield CompletableFuture.completedFuture(PartyResult.INVITATION_SENT);
                        }
                        yield storage.fetchParty(sent.partyId().get())
                                .thenApply(currentPartyOptional -> {
                                    currentPartyOptional.ifPresent(currentParty ->
                                            notificationService.announceInviteSent(
                                                    currentParty,
                                                    sender,
                                                    target
                                            ));
                                    return PartyResult.INVITATION_SENT;
                                });
                    }
                    case SendInvitationOutcome.TargetAlreadyInParty targetAlreadyInParty ->
                            CompletableFuture.completedFuture(PartyResult.TARGET_ALREADY_IN_PARTY);
                    case SendInvitationOutcome.PartyFull partyFull ->
                            CompletableFuture.completedFuture(PartyResult.PARTY_FULL);
                    case SendInvitationOutcome.CooldownActive cooldownActive ->
                            CompletableFuture.completedFuture(PartyResult.INVITATION_COOLDOWN_ACTIVE);
                    case SendInvitationOutcome.SenderLimitReached senderLimitReached ->
                            CompletableFuture.completedFuture(PartyResult.SENDER_INVITATION_LIMIT_REACHED);
                    case SendInvitationOutcome.ReceiverLimitReached receiverLimitReached ->
                            CompletableFuture.completedFuture(PartyResult.RECEIVER_INVITATION_LIMIT_REACHED);
                    case SendInvitationOutcome.AlreadyInvited alreadyInvited ->
                            CompletableFuture.completedFuture(PartyResult.ALREADY_INVITED);
                    case SendInvitationOutcome.InvitesDisabled invitesDisabled ->
                            CompletableFuture.completedFuture(
                                    "friend".equals(invitesDisabled.privacy())
                                            ? PartyResult.INVITES_FRIENDS_ONLY
                                            : PartyResult.INVITES_DISABLED
                            );
                    case SendInvitationOutcome.SenderInsufficientRole senderInsufficientRole ->
                            CompletableFuture.completedFuture(PartyResult.INSUFFICIENT_ROLE);
                    case SendInvitationOutcome.PartyNoLongerExists partyNoLongerExists ->
                            CompletableFuture.completedFuture(PartyResult.PARTY_NOT_FOUND);
                });
    }

    public @NonNull CompletableFuture<PartyResults.AcceptInvitation> acceptInvitation(
            @NonNull Player sender,
            @Nullable Player target
    ) {
        return acceptInvitationLegacy(sender, target).thenApply(PartyResults.AcceptInvitation::from);
    }

    private @NonNull CompletableFuture<PartyResult> acceptInvitationLegacy(
            @NonNull Player sender,
            @Nullable Player target
    ) {
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
        return storage.findInvitationFromSender(senderId, targetId)
                .thenCompose(invitationOptional -> {
                    if (invitationOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NO_INVITATION_FOUND);
                    }
                    PartyInvitation invitation = invitationOptional.get();
                    return addMemberToParty(senderId, invitation);
                });
    }

    private @NonNull CompletableFuture<PartyResult> addMemberToParty(@NonNull UUID playerId, @NonNull PartyInvitation invitation) {
        return storage.acceptInvitationAndJoin(playerId, invitation.senderId())
                .thenCompose(outcome -> switch (outcome) {
                    case JoinOutcome.Joined joined -> storage.fetchParty(joined.partyId())
                            .thenApply(partyOptional -> {
                                partyOptional.ifPresent(party ->
                                        proxyServer.getPlayer(playerId).ifPresent(player ->
                                                notificationService.notifyMemberJoined(party, player)));
                                return PartyResult.JOINED_PARTY;
                            });
                    case JoinOutcome.PartyFull partyFull -> CompletableFuture.completedFuture(PartyResult.PARTY_FULL);
                    case JoinOutcome.AlreadyMember alreadyMember ->
                            CompletableFuture.completedFuture(PartyResult.ALREADY_IN_PARTY);
                    case JoinOutcome.InvitationExpired invitationExpired ->
                            CompletableFuture.completedFuture(PartyResult.NO_INVITATION_FOUND);
                    case JoinOutcome.InvitationNoLongerValid invitationNoLongerValid ->
                            CompletableFuture.completedFuture(PartyResult.NO_INVITATION_FOUND);
                });
    }

    public @NonNull CompletableFuture<PartyResults.JoinPublic> joinPublicParty(
            @NonNull Player sender,
            @Nullable Player targetLeader
    ) {
        if (targetLeader == null) {
            return CompletableFuture.completedFuture(new PartyResults.JoinPublic.PlayerNotFound());
        }

        UUID senderId = sender.getUniqueId();
        return storage.getPlayerParty(targetLeader.getUniqueId())
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()
                            || !partyOptional.get().isLeader(targetLeader.getUniqueId())) {
                        return CompletableFuture.completedFuture(
                                new PartyResults.JoinPublic.TargetNotLeader());
                    }

                    Party party = partyOptional.get();
                    return storage.tryJoinPublicParty(
                                    party.partyId(),
                                    targetLeader.getUniqueId(),
                                    senderId
                            )
                            .thenCompose(outcome -> switch (outcome) {
                                case JoinPublicPartyOutcome.Joined ignored ->
                                        completePublicPartyJoin(
                                                party.partyId(),
                                                sender,
                                                targetLeader.getUsername()
                                        );
                                case JoinPublicPartyOutcome.AlreadyInParty ignored ->
                                        CompletableFuture.completedFuture(
                                                new PartyResults.JoinPublic.AlreadyInParty());
                                case JoinPublicPartyOutcome.PartyFull ignored ->
                                        CompletableFuture.completedFuture(
                                                new PartyResults.JoinPublic.PartyFull());
                                case JoinPublicPartyOutcome.PartyPrivate ignored ->
                                        CompletableFuture.completedFuture(
                                                new PartyResults.JoinPublic.PartyPrivate());
                                case JoinPublicPartyOutcome.PartyNotFound ignored ->
                                        CompletableFuture.completedFuture(
                                                new PartyResults.JoinPublic.PartyNotFound());
                                case JoinPublicPartyOutcome.TargetNotLeader ignored ->
                                        CompletableFuture.completedFuture(
                                                new PartyResults.JoinPublic.TargetNotLeader());
                            });
                });
    }

    private @NonNull CompletableFuture<PartyResults.JoinPublic> completePublicPartyJoin(
            @NonNull UUID partyId,
            @NonNull Player joiningPlayer,
            @NonNull String fallbackLeaderName
    ) {
        return storage.fetchParty(partyId)
                .thenApply(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return new PartyResults.JoinPublic.PartyNotFound();
                    }

                    Party updatedParty = partyOptional.get();
                    notificationService.notifyMemberJoined(updatedParty, joiningPlayer);
                    String leaderName = proxyServer.getPlayer(updatedParty.leaderId())
                            .map(Player::getUsername)
                            .orElse(fallbackLeaderName);
                    return new PartyResults.JoinPublic.Joined(leaderName);
                });
    }

    public @NonNull CompletableFuture<PartyResults.RejectInvitation> rejectInvitation(
            @NonNull Player sender,
            @Nullable Player target
    ) {
        return rejectInvitationLegacy(sender, target).thenApply(PartyResults.RejectInvitation::from);
    }

    private @NonNull CompletableFuture<PartyResult> rejectInvitationLegacy(
            @NonNull Player sender,
            @Nullable Player target
    ) {
        if (target == null) {
            return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        return storage.findInvitationFromSender(senderId, targetId)
                .thenCompose(invitationOptional -> {
                    if (invitationOptional.isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.NO_INVITATION_FOUND);
                    }
                    PartyInvitation invitation = invitationOptional.get();
                    return storage.removePendingInvitation(invitation.senderId(), senderId)
                            .thenApply(removed -> removed ? PartyResult.INVITATION_REJECTED : PartyResult.NO_INVITATION_FOUND);
                });
    }

    public @NonNull CompletableFuture<PartyResults.RevokeInvitation> revokeInvitation(
            @NonNull Player sender,
            @Nullable Player target
    ) {
        return revokeInvitationLegacy(sender, target).thenApply(PartyResults.RevokeInvitation::from);
    }

    private @NonNull CompletableFuture<PartyResult> revokeInvitationLegacy(
            @NonNull Player sender,
            @Nullable Player target
    ) {
        if (target == null) {
            return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
        }

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isPresent()
                            && !partyOptional.get().canManageMembers(senderId)) {
                        return CompletableFuture.completedFuture(PartyResult.INSUFFICIENT_ROLE);
                    }
                    return revokeInvitationFromStorage(
                            targetId,
                            partyOptional.map(Party::partyId),
                            senderId
                    );
                });
    }

    private @NonNull CompletableFuture<PartyResult> revokeInvitationFromStorage(
            @NonNull UUID targetId,
            @NonNull Optional<UUID> partyId,
            @NonNull UUID requesterId
    ) {
        return storage.tryRevokeInvitation(partyId, requesterId, targetId)
                .thenApply(outcome -> switch (outcome) {
                    case RevokePartyInvitationOutcome.Revoked ignored ->
                            PartyResult.INVITATION_REVOKED;
                    case RevokePartyInvitationOutcome.InvitationNotFound ignored ->
                            PartyResult.NO_INVITATION_FOUND;
                    case RevokePartyInvitationOutcome.InsufficientRole ignored ->
                            PartyResult.INSUFFICIENT_ROLE;
                    case RevokePartyInvitationOutcome.PartyNotFound ignored ->
                            PartyResult.PARTY_NOT_FOUND;
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
                    new PartyRequestsResult.InvalidRequestType());
        }

        return invitationsFuture.thenApply(invitations -> {
            if (invitations.isEmpty()) {
                return new PartyRequestsResult.Empty();
            }

            PaginationResult<PartyInvitation> pagination = PaginationResult.paginate(invitations, page, SharedConstants.ENTRIES_PER_PAGE);
            if (!pagination.isValidPage()) {
                return new PartyRequestsResult.InvalidPage(pagination);
            }
            return new PartyRequestsResult.Found(pagination);
        });
    }

    public @NonNull CompletableFuture<PartyResults.Leave> leaveParty(@NonNull Player sender) {
        return leavePartyLegacy(sender).thenApply(PartyResults.Leave::from);
    }

    private @NonNull CompletableFuture<PartyResult> leavePartyLegacy(@NonNull Player sender) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    return removePlayerFromParty(senderId, party)
                            .thenCompose(result -> {
                                if (result == PartyResult.LEADER_TRANSFERRED) {
                                    return storage.fetchParty(party.partyId())
                                            .thenApply(updatedPartyOptional -> {
                                                updatedPartyOptional.ifPresent(updatedParty -> {
                                                    notificationService.notifyMemberLeft(party, sender.getUsername(), senderId);
                                                    proxyServer.getPlayer(updatedParty.leaderId()).ifPresent(newLeader ->
                                                            notificationService.notifyLeadershipTransferred(updatedParty, sender.getUsername(), newLeader));
                                                });
                                                return PartyResult.LEFT_PARTY;
                                            });
                                } else if (result == PartyResult.LEFT_PARTY || result == PartyResult.LEFT_PARTY_DISBANDED) {
                                    notificationService.notifyMemberLeft(party, sender.getUsername(), senderId);
                                }
                                return CompletableFuture.completedFuture(result);
                            });
                });
    }

    public @NonNull CompletableFuture<PartyResults.KickMember> kickMember(
            @NonNull Player sender,
            @Nullable Player target,
            @Nullable String reason
    ) {
        return kickMemberLegacy(sender, target, reason).thenApply(PartyResults.KickMember::from);
    }

    private @NonNull CompletableFuture<PartyResult> kickMemberLegacy(
            @NonNull Player sender,
            @Nullable Player target,
            @Nullable String reason
    ) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (!party.canManageMembers(senderId)) {
                        return CompletableFuture.completedFuture(PartyResult.INSUFFICIENT_ROLE);
                    }
                    if (target == null) {
                        return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
                    }
                    UUID targetId = target.getUniqueId();
                    if (senderId.equals(targetId)) {
                        return CompletableFuture.completedFuture(PartyResult.CANNOT_KICK_SELF);
                    }
                    if (!party.isMember(targetId)) {
                        return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_IN_PARTY);
                    }
                    return storage.tryKickMember(party.partyId(), senderId, targetId)
                            .thenCompose(outcome -> switch (outcome) {
                                case KickPartyMemberOutcome.Kicked ignored -> {
                                    notificationService.notifyMemberKicked(party, target, reason);
                                    yield CompletableFuture.completedFuture(PartyResult.MEMBER_KICKED);
                                }
                                case KickPartyMemberOutcome.PartyNotFound ignored ->
                                        CompletableFuture.completedFuture(PartyResult.PARTY_NOT_FOUND);
                                case KickPartyMemberOutcome.InsufficientRole ignored ->
                                        CompletableFuture.completedFuture(PartyResult.INSUFFICIENT_ROLE);
                                case KickPartyMemberOutcome.MemberNotFound ignored ->
                                        CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_IN_PARTY);
                                case KickPartyMemberOutcome.CannotKickSelf ignored ->
                                        CompletableFuture.completedFuture(PartyResult.CANNOT_KICK_SELF);
                                case KickPartyMemberOutcome.CannotKickLeader ignored ->
                                        CompletableFuture.completedFuture(PartyResult.CANNOT_KICK_LEADER);
                                case KickPartyMemberOutcome.CannotKickModerator ignored ->
                                        CompletableFuture.completedFuture(PartyResult.CANNOT_KICK_MODERATOR);
                            });
                });
    }

    public @NonNull CompletableFuture<PartyResults.ChangeModeratorRole> promoteModerator(
            @NonNull Player sender,
            @Nullable Player target
    ) {
        return changeModeratorRole(sender, target, true);
    }

    public @NonNull CompletableFuture<PartyResults.ChangeModeratorRole> demoteModerator(
            @NonNull Player sender,
            @Nullable Player target
    ) {
        return changeModeratorRole(sender, target, false);
    }

    private @NonNull CompletableFuture<PartyResults.ChangeModeratorRole> changeModeratorRole(
            @NonNull Player sender,
            @Nullable Player target,
            boolean moderator
    ) {
        UUID senderId = sender.getUniqueId();
        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new PartyResults.ChangeModeratorRole.NotInParty());
                    }

                    Party party = partyOptional.get();
                    if (!party.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(
                                new PartyResults.ChangeModeratorRole.NotLeader());
                    }
                    if (target == null) {
                        return CompletableFuture.completedFuture(
                                new PartyResults.ChangeModeratorRole.PlayerNotFound());
                    }
                    UUID targetId = target.getUniqueId();
                    if (!party.isMember(targetId)) {
                        return CompletableFuture.completedFuture(
                                new PartyResults.ChangeModeratorRole.MemberNotFound());
                    }

                    return storage.updateModeratorStatus(
                                    party.partyId(),
                                    senderId,
                                    targetId,
                                    moderator
                            )
                            .thenApply(outcome -> switch (outcome) {
                                case PartyModeratorChangeOutcome.Changed ignored -> {
                                    notificationService.notifyModeratorStatusChanged(
                                            party,
                                            target.getUsername(),
                                            moderator
                                    );
                                    yield new PartyResults.ChangeModeratorRole.Changed(
                                            target.getUsername());
                                }
                                case PartyModeratorChangeOutcome.PartyNotFound ignored ->
                                        new PartyResults.ChangeModeratorRole.PartyNotFound();
                                case PartyModeratorChangeOutcome.NotLeader ignored ->
                                        new PartyResults.ChangeModeratorRole.NotLeader();
                                case PartyModeratorChangeOutcome.MemberNotFound ignored ->
                                        new PartyResults.ChangeModeratorRole.MemberNotFound();
                                case PartyModeratorChangeOutcome.CannotChangeLeader ignored ->
                                        new PartyResults.ChangeModeratorRole.CannotChangeLeader();
                                case PartyModeratorChangeOutcome.AlreadyModerator ignored ->
                                        new PartyResults.ChangeModeratorRole.AlreadyModerator();
                                case PartyModeratorChangeOutcome.NotModerator ignored ->
                                        new PartyResults.ChangeModeratorRole.NotModerator();
                            });
                });
    }

    public @NonNull CompletableFuture<PartyMembersResult> getPartyMembers(@NonNull Player sender, int page) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenApply(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return new PartyMembersResult.NotInParty();
                    }
                    Party party = partyOptional.get();
                    List<UUID> members = new ArrayList<>(party.getMemberIds());
                    // Sort: leader first, moderators second, then stable UUID ordering.
                    members.sort((a, b) -> {
                        if (party.isLeader(a)) return -1;
                        if (party.isLeader(b)) return 1;
                        if (party.isModerator(a) && !party.isModerator(b)) return -1;
                        if (!party.isModerator(a) && party.isModerator(b)) return 1;
                        return a.compareTo(b);
                    });

                    if (members.isEmpty()) {
                        return new PartyMembersResult.Empty(party);
                    }

                    PaginationResult<UUID> pagination = PaginationResult.paginate(members, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new PartyMembersResult.InvalidPage(pagination, party);
                    }
                    return new PartyMembersResult.Found(pagination, party);
                });
    }

    public @NonNull CompletableFuture<PartyResults.SendChat> sendPartyChat(
            @NonNull Player sender,
            @NonNull String message
    ) {
        return sendPartyChatLegacy(sender, message).thenApply(PartyResults.SendChat::from);
    }

    private @NonNull CompletableFuture<PartyResult> sendPartyChatLegacy(
            @NonNull Player sender,
            @NonNull String message
    ) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    if (message.length() > PartyProxyConstants.MAX_MESSAGE_LENGTH) {
                        return CompletableFuture.completedFuture(PartyResult.MESSAGE_TOO_LONG);
                    }
                    Party party = partyOptional.get();
                    Set<UUID> memberIds = party.getMemberIds();

                    return storage.fetchSettingsForMembers(memberIds)
                            .thenApply(memberSettingsMap -> {
                                PartySettings senderSettings = memberSettingsMap.getOrDefault(senderId, new PartySettings(senderId));

                                if (!senderSettings.allowChat()) {
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
                    return removePlayerFromParty(playerId, party)
                            .thenCompose(result -> {
                                if (result == PartyResult.LEADER_TRANSFERRED) {
                                    return storage.fetchParty(party.partyId())
                                            .thenAccept(updatedPartyOptional -> {
                                                updatedPartyOptional.ifPresent(updatedParty ->
                                                        notificationService.notifyLeaderDisconnected(updatedParty, playerId, username));
                                            });
                                } else if (result == PartyResult.LEFT_PARTY && !wasLeader) {
                                        return storage.fetchParty(party.partyId())
                                                .thenAccept(updatedPartyOptional -> {
                                                    updatedPartyOptional.ifPresent(updatedParty ->
                                                            notificationService.notifyMemberDisconnected(updatedParty, playerId, username));
                                                });
                                } else if (result == PartyResult.LEFT_PARTY_DISBANDED) {
                                    notificationService.notifyMemberDisconnected(
                                            party,
                                            playerId,
                                            username
                                    );
                                }
                                return CompletableFuture.<Void>completedFuture(null);
                            });
                });
    }

    private @NonNull CompletableFuture<PartyResult> removePlayerFromParty(
            @NonNull UUID memberId,
            @NonNull Party party
    ) {
        return storage.removeMember(party.partyId(), memberId)
                .thenApply(outcome -> switch (outcome) {
                    case RemoveMemberOutcome.MemberRemoved memberRemoved -> PartyResult.LEFT_PARTY;
                    case RemoveMemberOutcome.LeaderTransferred leaderTransferred -> PartyResult.LEADER_TRANSFERRED;
                    case RemoveMemberOutcome.PartyDisbanded partyDisbanded -> PartyResult.LEFT_PARTY_DISBANDED;
                    case RemoveMemberOutcome.MemberNotFound memberNotFound -> PartyResult.PLAYER_NOT_IN_PARTY;
                    case RemoveMemberOutcome.PartyNotFound partyNotFound -> PartyResult.PARTY_NOT_FOUND;
                });
    }

    public @NonNull CompletableFuture<PartyResults.TransferLeadership> transferLeadership(
            @NonNull Player sender,
            @Nullable Player target,
            boolean isConfirming
    ) {
        return transferLeadershipLegacy(sender, target, isConfirming)
                .thenApply(PartyResults.TransferLeadership::from);
    }

    private @NonNull CompletableFuture<PartyResult> transferLeadershipLegacy(
            @NonNull Player sender,
            @Nullable Player target,
            boolean isConfirming
    ) {
        UUID senderId = sender.getUniqueId();

        return storage.getPlayerParty(senderId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_IN_PARTY);
                    }
                    Party party = partyOptional.get();
                    if (!party.isLeader(senderId)) {
                        return CompletableFuture.completedFuture(PartyResult.NOT_LEADER);
                    }
                    if (target == null) {
                        return CompletableFuture.completedFuture(PartyResult.PLAYER_NOT_FOUND);
                    }
                    UUID targetId = target.getUniqueId();
                    if (senderId.equals(targetId)) {
                        return CompletableFuture.completedFuture(PartyResult.CANNOT_TRANSFER_TO_SELF);
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
                        String oldLeaderName = proxyServer.getPlayer(senderId)
                                .map(Player::getUsername)
                                .orElse("Unknown");
                        proxyServer.getPlayer(targetId).ifPresent(newLeader ->
                                notificationService.notifyLeadershipTransferred(party, oldLeaderName, newLeader));
                        yield PartyResult.LEADERSHIP_TRANSFERRED;
                    }
                    case TransferLeadershipOutcome.PartyNotFound partyNotFound -> PartyResult.PARTY_NOT_FOUND;
                    case TransferLeadershipOutcome.NotLeader notLeader -> PartyResult.NOT_LEADER;
                    case TransferLeadershipOutcome.TargetNotMember targetNotMember -> PartyResult.PLAYER_NOT_IN_PARTY;
                });
    }

    public @NonNull CompletableFuture<PartyResults.Warp> warpParty(@NonNull Player sender) {
        return warpPartyLegacy(sender).thenApply(PartyResults.Warp::from);
    }

    private @NonNull CompletableFuture<PartyResult> warpPartyLegacy(@NonNull Player sender) {
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
        return storage.checkAndUpdateLastWarpTime(
                        party.partyId(),
                        sender.getUniqueId(),
                        now,
                        PartyProxyConstants.WARP_COOLDOWN
                )
                .thenCompose(outcome -> switch (outcome) {
                    case WarpOutcome.Allowed allowed -> executePartyWarp(party, sender);
                    case WarpOutcome.OnCooldown onCooldown ->
                            CompletableFuture.<PartyResult>completedFuture(PartyResult.WARP_ON_COOLDOWN);
                    case WarpOutcome.NotLeader notLeader ->
                            CompletableFuture.<PartyResult>completedFuture(PartyResult.NOT_LEADER);
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

    public @NonNull CompletableFuture<PartyResults.JumpToLeader> jumpToLeader(@NonNull Player sender) {
        return jumpToLeaderLegacy(sender).thenApply(PartyResults.JumpToLeader::from);
    }

    private @NonNull CompletableFuture<PartyResult> jumpToLeaderLegacy(@NonNull Player sender) {
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

                    Optional<Player> currentLeader = proxyServer.getPlayer(leaderId);
                    if (currentLeader.isEmpty() || currentLeader.get().getCurrentServer().isEmpty()) {
                        return CompletableFuture.<PartyResult>completedFuture(PartyResult.LEADER_NOT_ONLINE);
                    }
                    Player actualLeader = currentLeader.get();

                    return sender.createConnectionRequest(actualLeader.getCurrentServer().get().getServer())
                            .connect()
                            .thenApply(result -> PartyResult.JUMPED_TO_LEADER)
                            .exceptionally(throwable -> PartyResult.WARP_FAILED);
                });
    }

    public @NonNull CompletableFuture<PartyResults.UpdateSetting> updateBooleanSetting(
            @NonNull UUID playerId,
            @NonNull String settingName,
            boolean value
    ) {
        return updateBooleanSettingLegacy(playerId, settingName, value)
                .thenApply(PartyResults.UpdateSetting::from);
    }

    private @NonNull CompletableFuture<PartyResult> updateBooleanSettingLegacy(
            @NonNull UUID playerId,
            @NonNull String settingName,
            boolean value
    ) {
        if (!settingName.equals("allow_chat")
                && !settingName.equals("allow_warp")
                && !settingName.equals("auto_warp")) {
            return CompletableFuture.completedFuture(PartyResult.INVALID_SETTING);
        }

        CompletableFuture<Void> updateFuture = switch (settingName) {
            case "allow_chat" -> storage.updateAllowChat(playerId, value);
            case "allow_warp" -> storage.updateAllowWarp(playerId, value);
            case "auto_warp" -> storage.updateAutoWarp(playerId, value);
            default -> throw new IllegalArgumentException("Unknown setting: " + settingName);
        };

        return updateFuture
                .thenApply(ignored -> PartyResult.SETTING_UPDATED)
                .exceptionally(throwable -> PartyResult.INVALID_SETTING);
    }

    public @NonNull CompletableFuture<PartyResults.UpdateSetting> updateInvitePrivacy(
            @NonNull UUID playerId,
            @NonNull String value
    ) {
        return updateInvitePrivacyLegacy(playerId, value).thenApply(PartyResults.UpdateSetting::from);
    }

    private @NonNull CompletableFuture<PartyResult> updateInvitePrivacyLegacy(
            @NonNull UUID playerId,
            @NonNull String value
    ) {
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        if (!normalizedValue.equals("all")
                && !normalizedValue.equals("friend")
                && !normalizedValue.equals("none")) {
            return CompletableFuture.completedFuture(PartyResult.INVALID_SETTING);
        }

        return storage.updateInvitePrivacy(playerId, normalizedValue)
                .thenApply(ignored -> PartyResult.SETTING_UPDATED)
                .exceptionally(throwable -> PartyResult.INVALID_SETTING);
    }

    public @NonNull CompletableFuture<PartySettings> getSettings(@NonNull UUID playerId) {
        return storage.fetchSettings(playerId)
                .thenApply(settingsOptional -> settingsOptional.orElse(new PartySettings(playerId)));
    }

    public @NonNull CompletableFuture<Optional<PartyGroupSettings>> getGroupSettingsForPlayer(
            @NonNull UUID playerId
    ) {
        return storage.getPlayerParty(playerId)
                .thenCompose(partyOptional -> partyOptional
                        .map(party -> storage.fetchGroupSettings(party.partyId()))
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    public @NonNull CompletableFuture<PartyResults.UpdateSetting> updateGroupPrivacy(
            @NonNull UUID playerId,
            @NonNull String value
    ) {
        return storage.getPlayerParty(playerId)
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new PartyResults.UpdateSetting.NotInParty());
                    }
                    Optional<GroupJoinPolicy> joinPolicy = GroupJoinPolicy.fromInput(value);
                    if (joinPolicy.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new PartyResults.UpdateSetting.InvalidSetting());
                    }
                    return storage.updateJoinPolicy(
                                    partyOptional.get().partyId(),
                                    playerId,
                                    joinPolicy.get()
                            )
                            .thenApply(outcome -> switch (outcome) {
                                case UpdatePartyJoinPolicyOutcome.Updated ignored ->
                                        new PartyResults.UpdateSetting.Updated();
                                case UpdatePartyJoinPolicyOutcome.NotLeader ignored ->
                                        new PartyResults.UpdateSetting.NotLeader();
                                case UpdatePartyJoinPolicyOutcome.PartyNotFound ignored ->
                                        new PartyResults.UpdateSetting.PartyNotFound();
                            });
                });
    }

    public @NonNull CompletableFuture<Void> handleServerSwitch(
            @NonNull Player player,
            @NonNull RegisteredServer destinationServer
    ) {
        UUID playerId = player.getUniqueId();
        return getSettings(playerId)
                .thenCompose(settings -> settings.autoWarp()
                        ? storage.getPlayerParty(playerId)
                        : CompletableFuture.completedFuture(Optional.empty()))
                .thenCompose(partyOptional -> {
                    if (partyOptional.isEmpty() || !partyOptional.get().isLeader(playerId)) {
                        return CompletableFuture.completedFuture(null);
                    }

                    boolean stillOnDestination = player.getCurrentServer()
                            .map(ServerConnection::getServer)
                            .filter(destinationServer::equals)
                            .isPresent();
                    if (!stillOnDestination) {
                        return CompletableFuture.completedFuture(null);
                    }

                    return checkAndExecuteWarp(partyOptional.get(), player)
                            .thenAccept(ignored -> {
                            });
                });
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

    public void cleanupExpiredInvitations() {
        storage.cleanupExpiredInvitations(Instant.now(), PartyProxyConstants.INVITATION_EXPIRY)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to cleanup expired party invitations", throwable);
                    return null;
                });
    }

    public void cleanupExpiredConfirmations() {
        storage.cleanupExpiredConfirmations(Instant.now(), PartyProxyConstants.CONFIRMATION_EXPIRY)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to cleanup expired party confirmations", throwable);
                    return null;
                });
    }

    public void cleanupExpiredCooldowns() {
        storage.cleanupExpiredCooldowns(Instant.now(), PartyProxyConstants.INVITATION_COOLDOWN)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to cleanup expired party cooldowns", throwable);
                    return null;
                });
    }
}
