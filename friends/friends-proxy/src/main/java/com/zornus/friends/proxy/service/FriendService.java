package com.zornus.friends.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.*;
import com.zornus.friends.proxy.model.result.FriendListResult;
import com.zornus.friends.proxy.model.result.FriendRequestListResult;
import com.zornus.friends.proxy.model.result.FriendResult;
import com.zornus.friends.proxy.storage.AcceptRequestOutcome;
import com.zornus.friends.proxy.storage.FriendStorage;
import com.zornus.friends.proxy.storage.SendRequestOutcome;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FriendService implements AutoCloseable {

    private final @NonNull FriendStorage storage;
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull FriendNotificationService notificationService;

    public FriendService(@NonNull FriendStorage storage, @NonNull ProxyServer proxyServer) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.notificationService = new FriendNotificationService(storage, proxyServer);
    }

    @Override
    public void close() {
        storage.close();
    }


    public @NonNull FriendNotificationService getNotificationService() {
        return notificationService;
    }

    // ========================================
    // FRIEND REQUESTS
    // ========================================

    public @NonNull CompletableFuture<FriendResult> sendFriendRequest(@NonNull UUID senderUuid, @NonNull UUID targetUuid) {
        if (senderUuid.equals(targetUuid)) {
            return CompletableFuture.completedFuture(FriendResult.CANNOT_ADD_SELF);
        }

        return storage.trySendFriendRequest(senderUuid, targetUuid)
                .thenApply(outcome -> switch (outcome) {
                    case SendRequestOutcome.Sent sent -> {
                        notificationService.notifyFriendRequestReceived(targetUuid, senderUuid);
                        yield FriendResult.REQUEST_SENT;
                    }
                    case SendRequestOutcome.RequestAcceptedAutomatically auto -> {
                        notificationService.notifyFriendRequestAccepted(targetUuid, senderUuid);
                        notificationService.notifyFriendRequestAccepted(senderUuid, targetUuid);
                        yield FriendResult.REQUEST_ACCEPTED_AUTOMATICALLY;
                    }
                    case SendRequestOutcome.AlreadyFriends already -> FriendResult.ALREADY_FRIENDS;
                    case SendRequestOutcome.RequestAlreadySent alreadySent -> FriendResult.REQUEST_ALREADY_SENT;
                    case SendRequestOutcome.SenderRequestLimitReached senderLimit -> FriendResult.SENDER_REQUEST_LIMIT_REACHED;
                    case SendRequestOutcome.ReceiverRequestLimitReached receiverLimit -> FriendResult.RECEIVER_REQUEST_LIMIT_REACHED;
                    case SendRequestOutcome.SenderFriendsLimitReached senderFriends -> FriendResult.SENDER_FRIENDS_LIMIT_REACHED;
                    case SendRequestOutcome.ReceiverFriendsLimitReached receiverFriends -> FriendResult.RECEIVER_FRIENDS_LIMIT_REACHED;
                    case SendRequestOutcome.RequestCooldownActive cooldown -> FriendResult.REQUEST_COOLDOWN_ACTIVE;
                    case SendRequestOutcome.PlayerNotAcceptingRequests notAccepting -> FriendResult.PLAYER_NOT_ACCEPTING_REQUESTS;
                    case SendRequestOutcome.RequestNoLongerValid noLongerValid -> FriendResult.REQUEST_NO_LONGER_VALID;
                });
    }

    public @NonNull CompletableFuture<FriendResult> acceptFriendRequest(@NonNull UUID accepterUuid,
                                                                        @NonNull UUID requesterUuid) {
        return storage.acceptFriendRequest(accepterUuid, requesterUuid)
                .thenApply(outcome -> switch (outcome) {
                    case AcceptRequestOutcome.Accepted accepted -> {
                        notificationService.notifyFriendRequestAccepted(requesterUuid, accepterUuid);
                        yield FriendResult.REQUEST_ACCEPTED;
                    }
                    case AcceptRequestOutcome.NoRequestFound notFound -> FriendResult.NO_REQUEST_FOUND;
                    case AcceptRequestOutcome.AlreadyFriends already -> FriendResult.ALREADY_FRIENDS;
                    case AcceptRequestOutcome.AccepterFriendsLimitReached accepterLimit -> FriendResult.SENDER_FRIENDS_LIMIT_REACHED;
                    case AcceptRequestOutcome.RequesterFriendsLimitReached requesterLimit -> FriendResult.RECEIVER_FRIENDS_LIMIT_REACHED;
                });
    }

    public @NonNull CompletableFuture<FriendResult> rejectFriendRequest(@NonNull UUID rejecterUuid, @NonNull UUID requesterUuid) {
        return storage.removeFriendRequest(requesterUuid, rejecterUuid)
                .thenApply(removed -> removed ? FriendResult.REQUEST_REJECTED : FriendResult.NO_REQUEST_FOUND);
    }

    public @NonNull CompletableFuture<FriendResult> revokeFriendRequest(@NonNull UUID revokerUuid, @NonNull UUID targetUuid) {
        return storage.removeFriendRequest(revokerUuid, targetUuid)
                .thenApply(removed -> removed ? FriendResult.REQUEST_REVOKED : FriendResult.NO_REQUEST_FOUND);
    }

    public @NonNull CompletableFuture<FriendRequestListResult> getIncomingRequestsList(@NonNull UUID playerUuid, int page) {
        return storage.fetchIncomingFriendRequests(playerUuid)
                .thenApply(requests -> {
                    if (requests.isEmpty()) {
                        return new FriendRequestListResult(FriendResult.LIST_EMPTY, PaginationResult.invalidPage(1));
                    }
                    PaginationResult<FriendRequest> pagination = PaginationResult.paginate(requests, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new FriendRequestListResult(FriendResult.INVALID_PAGE, pagination);
                    }
                    return new FriendRequestListResult(FriendResult.SUCCESS, pagination);
                });
    }

    public @NonNull CompletableFuture<FriendRequestListResult> getOutgoingRequestsList(@NonNull UUID playerUuid, int page) {
        return storage.fetchOutgoingFriendRequests(playerUuid)
                .thenApply(requests -> {
                    if (requests.isEmpty()) {
                        return new FriendRequestListResult(FriendResult.LIST_EMPTY, PaginationResult.invalidPage(1));
                    }
                    PaginationResult<FriendRequest> pagination = PaginationResult.paginate(requests, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new FriendRequestListResult(FriendResult.INVALID_PAGE, pagination);
                    }
                    return new FriendRequestListResult(FriendResult.SUCCESS, pagination);
                });
    }

    public @NonNull CompletableFuture<FriendResult> removeFriend(@NonNull UUID removerUuid, @NonNull UUID friendUuid) {
        return storage.removeFriendRelation(removerUuid, friendUuid)
                .thenApply(removed -> removed ? FriendResult.FRIEND_REMOVED : FriendResult.NOT_FRIENDS);
    }

    // ========================================
    // FRIEND RELATIONS
    // ========================================

    public @NonNull CompletableFuture<Boolean> areFriends(@NonNull UUID player1Uuid, @NonNull UUID player2Uuid) {
        return storage.hasFriendRelation(player1Uuid, player2Uuid);
    }

    public @NonNull CompletableFuture<FriendListResult> getFriendsList(@NonNull UUID playerUuid, int page) {
        return storage.fetchFriendRelations(playerUuid)
                .thenApply(relations -> {
                    if (relations.isEmpty()) {
                        return new FriendListResult(FriendResult.LIST_EMPTY, PaginationResult.invalidPage(1));
                    }
                    PaginationResult<FriendRelation> pagination = PaginationResult.paginate(relations, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new FriendListResult(FriendResult.INVALID_PAGE, pagination);
                    }
                    return new FriendListResult(FriendResult.SUCCESS, pagination);
                });
    }

    public @NonNull CompletableFuture<FriendResult> sendFriendMessage(@NonNull UUID senderUuid, @NonNull UUID targetUuid,
                                                                      @NonNull String message) {
        if (message.length() > FriendProxyConstants.MAX_MESSAGE_LENGTH) {
            return CompletableFuture.completedFuture(FriendResult.MESSAGE_TOO_LONG);
        }

        return storage.hasFriendRelation(senderUuid, targetUuid)
                .thenCompose(areFriends -> validateMessagePreconditions(areFriends, targetUuid))
                .thenCompose(validationResult -> {
                    if (validationResult.result() != FriendResult.SUCCESS) {
                        return CompletableFuture.completedFuture(validationResult.result());
                    }
                    return deliverMessage(senderUuid, targetUuid, message, validationResult.targetPlayer());
                });
    }

    // ========================================
    // MESSAGING
    // ========================================

    private @NonNull CompletableFuture<MessageValidationResult> validateMessagePreconditions(boolean areFriends, @NonNull UUID targetUuid) {
        if (!areFriends) {
            return CompletableFuture.completedFuture(new MessageValidationResult(FriendResult.NOT_FRIENDS, null));
        }
        return storage.fetchSettings(targetUuid)
                .thenApply(settingsOpt -> {
                    FriendSettings settings = settingsOpt.orElse(new FriendSettings(targetUuid));
                    if (!settings.allowMessages()) {
                        return new MessageValidationResult(FriendResult.PLAYER_NOT_ACCEPTING_MESSAGES, null);
                    }
                    Optional<Player> targetPlayer = proxyServer.getPlayer(targetUuid);
                    if (targetPlayer.isEmpty()) {
                        return new MessageValidationResult(FriendResult.FRIEND_NOT_ONLINE, null);
                    }
                    return new MessageValidationResult(FriendResult.SUCCESS, targetPlayer.get());
                });
    }

    private @NonNull CompletableFuture<FriendResult> deliverMessage(@NonNull UUID senderUuid, @NonNull UUID targetUuid,
                                                                    @NonNull String message,
                                                                    @NonNull Player targetPlayer) {
        return storage.saveLastMessageSender(targetUuid, senderUuid)
                .thenApply(ignored -> {
                    notificationService.notifyFriendMessageReceived(targetPlayer, senderUuid, message);
                    return FriendResult.MESSAGE_SENT;
                });
    }

    public @NonNull CompletableFuture<FriendResult> sendFriendReply(@NonNull UUID senderUuid, @NonNull String message) {
        if (message.length() > FriendProxyConstants.MAX_MESSAGE_LENGTH) {
            return CompletableFuture.completedFuture(FriendResult.MESSAGE_TOO_LONG);
        }

        return storage.fetchLastMessageSender(senderUuid)
                .thenCompose(lastSenderOpt -> {
                    if (lastSenderOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(FriendResult.NO_RECENT_MESSAGE);
                    }
                    UUID targetUuid = lastSenderOpt.get();
                    Optional<Player> targetPlayer = proxyServer.getPlayer(targetUuid);
                    if (targetPlayer.isEmpty()) {
                        return CompletableFuture.completedFuture(FriendResult.FRIEND_NOT_ONLINE);
                    }

                    return sendFriendMessage(senderUuid, targetUuid, message);
                });
    }

    public @NonNull CompletableFuture<FriendResult> jumpToFriend(@NonNull UUID jumperUuid, @NonNull UUID targetUuid) {
        return storage.hasFriendRelation(jumperUuid, targetUuid)
                .thenCompose(areFriends -> validateJumpPreconditions(areFriends, jumperUuid, targetUuid))
                .thenCompose(validationResult -> {
                    if (validationResult.result() != FriendResult.SUCCESS) {
                        return CompletableFuture.completedFuture(validationResult.result());
                    }
                    return executeJump(validationResult.jumper(), validationResult.target());
                });
    }

    private @NonNull CompletableFuture<JumpValidationResult> validateJumpPreconditions(boolean areFriends, @NonNull UUID jumperUuid, @NonNull UUID targetUuid) {
        if (!areFriends) {
            return CompletableFuture.completedFuture(new JumpValidationResult(FriendResult.NOT_FRIENDS, null, null));
        }
        return storage.fetchSettings(targetUuid)
                .thenApply(settingsOpt -> {
                    FriendSettings settings = settingsOpt.orElse(new FriendSettings(targetUuid));
                    if (!settings.allowJump()) {
                        return new JumpValidationResult(FriendResult.PLAYER_NOT_ALLOWING_JUMP, null, null);
                    }

                    Optional<Player> targetPlayer = proxyServer.getPlayer(targetUuid);
                    if (targetPlayer.isEmpty()) {
                        return new JumpValidationResult(FriendResult.FRIEND_NOT_ONLINE, null, null);
                    }

                    Player target = targetPlayer.get();
                    Optional<Player> jumper = proxyServer.getPlayer(jumperUuid);
                    if (jumper.isEmpty()) {
                        return new JumpValidationResult(FriendResult.PLAYER_NOT_ONLINE, null, null);
                    }

                    if (target.getCurrentServer().isEmpty()) {
                        return new JumpValidationResult(FriendResult.FRIEND_NO_INSTANCE, null, null);
                    }

                    String targetServer = target.getCurrentServer().get().getServerInfo().getName();
                    Optional<String> jumperServer = jumper.get().getCurrentServer().map(s -> s.getServerInfo().getName());

                    if (jumperServer.isPresent() && jumperServer.get().equals(targetServer)) {
                        return new JumpValidationResult(FriendResult.ALREADY_IN_SAME_INSTANCE, null, null);
                    }

                    return new JumpValidationResult(FriendResult.SUCCESS, jumper.get(), target);
                });
    }

    // ========================================
    // JUMPING
    // ========================================

    private @NonNull CompletableFuture<FriendResult> executeJump(@NonNull Player jumper, @NonNull Player target) {
        return jumper.createConnectionRequest(target.getCurrentServer().get().getServer())
                .connect()
                .thenApply(result -> FriendResult.JUMP_SUCCESSFUL)
                .exceptionally(throwable -> FriendResult.JUMP_FAILED);
    }

    public @NonNull CompletableFuture<Optional<FriendSettings>> getSettings(@NonNull UUID playerUuid) {
        return storage.fetchSettings(playerUuid)
                .thenApply(settingsOpt -> settingsOpt.or(() -> Optional.of(new FriendSettings(playerUuid))));
    }

    public @NonNull CompletableFuture<Optional<Instant>> fetchLastSeen(@NonNull UUID playerUuid) {
        return storage.fetchLastSeen(playerUuid);
    }

    public @NonNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(@NonNull String username) {
        return storage.fetchPlayerByUsername(username);
    }

    // ========================================
    // SETTINGS
    // ========================================

    public @NonNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUuid(@NonNull UUID playerUuid) {
        return storage.fetchPlayerByUuid(playerUuid);
    }

    public @NonNull CompletableFuture<Optional<UUID>> fetchLastMessageSender(@NonNull UUID playerUuid) {
        return storage.fetchLastMessageSender(playerUuid);
    }

    public @NonNull CompletableFuture<FriendResult> updateSetting(@NonNull UUID playerUuid, @NonNull String setting, boolean value) {
        return applySettingUpdateAtomic(playerUuid, setting, value)
                .thenApply(success -> success ? FriendResult.SETTING_UPDATED : FriendResult.INVALID_SETTING);
    }

    public @NonNull CompletableFuture<FriendResult> setPresence(@NonNull UUID playerUuid, @NonNull PresenceState presenceState) {
        return storage.updatePresenceState(playerUuid, presenceState)
                .thenApply(ignored -> FriendResult.STATUS_UPDATED);
    }

    private @NonNull CompletableFuture<Boolean> applySettingUpdateAtomic(@NonNull UUID playerUuid, @NonNull String setting, boolean value) {
        return switch (setting.toLowerCase()) {
            case "messaging" -> storage.updateAllowMessages(playerUuid, value).thenApply(ignored -> true);
            case "jumping" -> storage.updateAllowJump(playerUuid, value).thenApply(ignored -> true);
            case "lastseen" -> storage.updateShowLastSeen(playerUuid, value).thenApply(ignored -> true);
            case "location" -> storage.updateShowLocation(playerUuid, value).thenApply(ignored -> true);
            case "requests" -> storage.updateAllowRequests(playerUuid, value).thenApply(ignored -> true);
            default -> CompletableFuture.completedFuture(false);
        };
    }

    public @NonNull CompletableFuture<Void> handlePlayerConnect(@NonNull UUID playerUuid, @NonNull String username) {
        return storage.upsertPlayer(playerUuid, username)
                .thenCompose(ignored -> storage.fetchFriendRelations(playerUuid))
                .thenAccept(friendRelations -> notificationService.notifyFriendsOfPlayerJoin(playerUuid, friendRelations));
    }

    public @NonNull CompletableFuture<Void> handlePlayerDisconnect(@NonNull UUID playerUuid) {
        return storage.saveLastSeen(playerUuid, Instant.now())
                .thenCompose(ignored -> storage.fetchFriendRelations(playerUuid))
                .thenAccept(friendRelations -> notificationService.notifyFriendsOfPlayerLeave(playerUuid, friendRelations));
    }

    public @NonNull CompletableFuture<Void> cleanupExpiredRequests() {
        return storage.cleanupExpiredFriendRequests(Instant.now(), FriendProxyConstants.REQUEST_EXPIRY_DURATION);
    }

    // ========================================
    // PLAYER LIFECYCLE
    // ========================================

    public @NonNull CompletableFuture<Void> cleanupExpiredCooldowns() {
        return storage.cleanupExpiredFriendRequestCooldowns(Instant.now(), FriendProxyConstants.COOLDOWN_EXPIRY_DURATION);
    }

    public @NonNull CompletableFuture<Void> cleanupExpiredLastMessageSenders() {
        return storage.cleanupExpiredLastMessageSenders(Instant.now(), FriendProxyConstants.LAST_MESSAGE_SENDER_RETENTION);
    }

    // ========================================
    // HELPERS
    // ========================================

    private record MessageValidationResult(FriendResult result, Player targetPlayer) {
    }

    private record JumpValidationResult(FriendResult result, Player jumper, Player target) {
    }
}
