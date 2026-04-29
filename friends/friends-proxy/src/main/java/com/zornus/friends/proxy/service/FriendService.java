package com.zornus.friends.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.*;
import com.zornus.friends.proxy.model.result.FriendListResult;
import com.zornus.friends.proxy.model.result.FriendRequestListResult;
import com.zornus.friends.proxy.model.result.FriendResult;
import com.zornus.friends.proxy.storage.FriendStorage;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.PaginationResult;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FriendService {

    private final @NotNull FriendStorage storage;
    private final @NotNull ProxyServer proxyServer;
    private final @NotNull FriendNotificationService notificationService;

    public FriendService(@NotNull FriendStorage storage, @NotNull ProxyServer proxyServer) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.notificationService = new FriendNotificationService(storage, proxyServer);
    }

    public void closeStorage() {
        storage.close();
    }


    public @NotNull FriendNotificationService getNotificationService() {
        return notificationService;
    }

    // ========================================
    // FRIEND REQUESTS
    // ========================================

    public @NotNull CompletableFuture<FriendResult> sendFriendRequest(@NotNull UUID senderUuid, @NotNull UUID targetUuid) {
        if (senderUuid.equals(targetUuid)) {
            return CompletableFuture.completedFuture(FriendResult.CANNOT_ADD_SELF);
        }

        return validateSendRequestPreconditions(senderUuid, targetUuid)
                .thenCompose(preconditionResult -> {
                    if (preconditionResult.isAlreadyFriendsOrHasOutgoing()) {
                        return CompletableFuture.completedFuture(preconditionResult.result());
                    }
                    return storage.hasIncomingFriendRequest(senderUuid, targetUuid)
                            .thenCompose(hasIncoming -> handleMutualRequestFlow(senderUuid, targetUuid, hasIncoming));
                });
    }

    private @NotNull CompletableFuture<PreconditionResult> validateSendRequestPreconditions(@NotNull UUID senderUuid, @NotNull UUID targetUuid) {
        return storage.hasFriendRelation(senderUuid, targetUuid)
                .thenCompose(areFriends -> {
                    if (areFriends) {
                        return CompletableFuture.completedFuture(new PreconditionResult(FriendResult.ALREADY_FRIENDS, true));
                    }
                    return storage.hasOutgoingFriendRequest(senderUuid, targetUuid)
                            .thenApply(hasOutgoing -> new PreconditionResult(
                                    hasOutgoing ? FriendResult.REQUEST_ALREADY_SENT : FriendResult.SUCCESS,
                                    hasOutgoing
                            ));
                });
    }

    private @NotNull CompletableFuture<FriendResult> handleMutualRequestFlow(@NotNull UUID senderUuid,
                                                                             @NotNull UUID targetUuid,
                                                                             boolean hasIncomingRequest) {
        if (hasIncomingRequest) {
            return handleMutualAutoAccept(senderUuid, targetUuid);
        }
        return continueRequestValidation(senderUuid, targetUuid)
                .thenCompose(validationResult -> {
                    if (validationResult != FriendResult.SUCCESS) {
                        return CompletableFuture.completedFuture(validationResult);
                    }
                    return createAndSendRequest(senderUuid, targetUuid);
                });
    }

    private @NotNull CompletableFuture<FriendResult> handleMutualAutoAccept(@NotNull UUID senderUuid,
                                                                            @NotNull UUID targetUuid) {
        return checkFriendLimitsForBoth(senderUuid, targetUuid)
                .thenCompose(limitsResult -> {
                    if (limitsResult != FriendResult.SUCCESS) {
                        return CompletableFuture.completedFuture(limitsResult);
                    }
                    return storage.addFriendRelation(senderUuid, targetUuid)
                            .thenCompose(added -> {
                                if (!added) {
                                    return CompletableFuture.completedFuture(FriendResult.ALREADY_FRIENDS);
                                }
                                return removeFriendRequestPair(targetUuid, senderUuid)
                                        .thenApply(ignored -> {
                                            notificationService.notifyFriendRequestAccepted(targetUuid, senderUuid);
                                            notificationService.notifyFriendRequestAccepted(senderUuid, targetUuid);
                                            return FriendResult.REQUEST_ACCEPTED_AUTOMATICALLY;
                                        });
                            });
                });
    }

    private @NotNull CompletableFuture<FriendResult> continueRequestValidation(@NotNull UUID senderUuid, @NotNull UUID targetUuid) {
        return checkRequestCooldown(senderUuid, targetUuid)
                .thenCompose(cooldownResult -> {
                    if (cooldownResult != FriendResult.SUCCESS) {
                        return CompletableFuture.completedFuture(cooldownResult);
                    }
                    return checkRequestLimits(senderUuid, targetUuid);
                })
                .thenCompose(limitsResult -> {
                    if (limitsResult != FriendResult.SUCCESS) {
                        return CompletableFuture.completedFuture(limitsResult);
                    }
                    return checkTargetAcceptsRequests(targetUuid);
                });
    }

    private @NotNull CompletableFuture<FriendResult> createAndSendRequest(@NotNull UUID senderUuid,
                                                                          @NotNull UUID targetUuid) {
        FriendRequest request = new FriendRequest(senderUuid, targetUuid);
        return storage.addFriendRequest(request)
                .thenCompose(added -> {
                    if (!added) {
                        return CompletableFuture.completedFuture(FriendResult.REQUEST_ALREADY_SENT);
                    }
                    return storage.saveFriendRequestTimestamp(senderUuid, targetUuid)
                            .thenApply(ignored -> {
                                notificationService.notifyFriendRequestReceived(targetUuid, senderUuid);
                                return FriendResult.REQUEST_SENT;
                            });
                });
    }

    private @NotNull CompletableFuture<FriendResult> checkRequestCooldown(@NotNull UUID senderUuid, @NotNull UUID targetUuid) {
        return storage.fetchFriendRequestTimestamp(senderUuid, targetUuid)
                .thenApply(lastTimestamp -> {
                    if (lastTimestamp.isEmpty()) {
                        return FriendResult.SUCCESS;
                    }
                    Instant nextAllowed = lastTimestamp.get().plus(FriendProxyConstants.FRIEND_REQUEST_COOLDOWN);
                    if (Instant.now().isBefore(nextAllowed)) {
                        return FriendResult.REQUEST_COOLDOWN_ACTIVE;
                    }
                    return FriendResult.SUCCESS;
                });
    }

    private @NotNull CompletableFuture<FriendResult> checkRequestLimits(@NotNull UUID senderUuid, @NotNull UUID targetUuid) {
        CompletableFuture<Integer> senderOutgoingCount = storage.countOutgoingFriendRequests(senderUuid);
        CompletableFuture<Integer> receiverIncomingCount = storage.countIncomingFriendRequests(targetUuid);

        return senderOutgoingCount.thenCombine(receiverIncomingCount, (senderOutgoing, receiverIncoming) -> {
            if (senderOutgoing >= FriendProxyConstants.MAX_FRIEND_REQUESTS) {
                return FriendResult.SENDER_REQUEST_LIMIT_REACHED;
            }
            if (receiverIncoming >= FriendProxyConstants.MAX_FRIEND_REQUESTS) {
                return FriendResult.RECEIVER_REQUEST_LIMIT_REACHED;
            }
            return FriendResult.SUCCESS;
        });
    }

    private @NotNull CompletableFuture<FriendResult> checkFriendLimitsForBoth(@NotNull UUID player1Uuid, @NotNull UUID player2Uuid) {
        return storage.fetchFriendRelationCount(player1Uuid)
                .thenCombine(storage.fetchFriendRelationCount(player2Uuid), (count1, count2) -> {
                    if (count1 >= FriendProxyConstants.MAX_FRIENDS) {
                        return FriendResult.SENDER_FRIENDS_LIMIT_REACHED;
                    }
                    if (count2 >= FriendProxyConstants.MAX_FRIENDS) {
                        return FriendResult.RECEIVER_FRIENDS_LIMIT_REACHED;
                    }
                    return FriendResult.SUCCESS;
                });
    }

    private @NotNull CompletableFuture<FriendResult> checkTargetAcceptsRequests(@NotNull UUID targetUuid) {
        return storage.fetchSettings(targetUuid)
                .thenApply(settingsOpt -> {
                    FriendSettings settings = settingsOpt.orElse(new FriendSettings(targetUuid));
                    if (!settings.allowRequests()) {
                        return FriendResult.PLAYER_NOT_ACCEPTING_REQUESTS;
                    }
                    return FriendResult.SUCCESS;
                });
    }

    public @NotNull CompletableFuture<FriendResult> acceptFriendRequest(@NotNull UUID accepterUuid,
                                                                        @NotNull UUID requesterUuid) {
        return storage.hasIncomingFriendRequest(accepterUuid, requesterUuid)
                .thenCompose(hasRequest -> {
                    if (!hasRequest) {
                        return CompletableFuture.completedFuture(FriendResult.NO_REQUEST_FOUND);
                    }
                    return storage.fetchFriendRelationCount(accepterUuid)
                            .thenCombine(storage.fetchFriendRelationCount(requesterUuid), (accepterCount, requesterCount) -> {
                                if (accepterCount >= FriendProxyConstants.MAX_FRIENDS) {
                                    return FriendResult.SENDER_FRIENDS_LIMIT_REACHED;
                                }
                                if (requesterCount >= FriendProxyConstants.MAX_FRIENDS) {
                                    return FriendResult.RECEIVER_FRIENDS_LIMIT_REACHED;
                                }
                                return FriendResult.SUCCESS;
                            });
                })
                .thenCompose(limitsResult -> {
                    if (limitsResult != FriendResult.SUCCESS) {
                        return CompletableFuture.completedFuture(limitsResult);
                    }
                    return storage.addFriendRelation(accepterUuid, requesterUuid)
                            .thenCompose(added -> {
                                if (!added) {
                                    return CompletableFuture.completedFuture(FriendResult.ALREADY_FRIENDS);
                                }
                                return storage.removeFriendRequest(requesterUuid, accepterUuid)
                                        .thenApply(ignored -> {
                                            notificationService.notifyFriendRequestAccepted(requesterUuid, accepterUuid);
                                            return FriendResult.REQUEST_ACCEPTED;
                                        });
                            });
                });
    }

    public @NotNull CompletableFuture<FriendResult> rejectFriendRequest(@NotNull UUID rejecterUuid, @NotNull UUID requesterUuid) {
        return storage.hasIncomingFriendRequest(rejecterUuid, requesterUuid)
                .thenCompose(hasRequest -> {
                    if (!hasRequest) {
                        return CompletableFuture.completedFuture(FriendResult.NO_REQUEST_FOUND);
                    }
                    return storage.removeFriendRequest(requesterUuid, rejecterUuid)
                            .thenApply(ignored -> FriendResult.REQUEST_REJECTED);
                });
    }

    public @NotNull CompletableFuture<FriendResult> revokeFriendRequest(@NotNull UUID revokerUuid, @NotNull UUID targetUuid) {
        return storage.hasOutgoingFriendRequest(revokerUuid, targetUuid)
                .thenCompose(hasRequest -> {
                    if (!hasRequest) {
                        return CompletableFuture.completedFuture(FriendResult.NO_REQUEST_FOUND);
                    }
                    return storage.removeFriendRequest(revokerUuid, targetUuid)
                            .thenApply(ignored -> FriendResult.REQUEST_REVOKED);
                });
    }

    public @NotNull CompletableFuture<FriendRequestListResult> getIncomingRequestsList(@NotNull UUID playerUuid, int page) {
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

    public @NotNull CompletableFuture<FriendRequestListResult> getOutgoingRequestsList(@NotNull UUID playerUuid, int page) {
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

    public @NotNull CompletableFuture<FriendResult> removeFriend(@NotNull UUID removerUuid, @NotNull UUID friendUuid) {
        return storage.hasFriendRelation(removerUuid, friendUuid)
                .thenCompose(areFriends -> {
                    if (!areFriends) {
                        return CompletableFuture.completedFuture(FriendResult.NOT_FRIENDS);
                    }
                    return storage.removeFriendRelation(removerUuid, friendUuid)
                            .thenApply(ignored -> FriendResult.FRIEND_REMOVED);
                });
    }

    // ========================================
    // FRIEND RELATIONS
    // ========================================

    public @NotNull CompletableFuture<Boolean> areFriends(@NotNull UUID player1Uuid, @NotNull UUID player2Uuid) {
        return storage.hasFriendRelation(player1Uuid, player2Uuid);
    }

    public @NotNull CompletableFuture<FriendListResult> getFriendsList(@NotNull UUID playerUuid, int page) {
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

    public @NotNull CompletableFuture<FriendResult> sendFriendMessage(@NotNull UUID senderUuid, @NotNull UUID targetUuid,
                                                                      @NotNull String message) {
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

    private @NotNull CompletableFuture<MessageValidationResult> validateMessagePreconditions(boolean areFriends, @NotNull UUID targetUuid) {
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

    private @NotNull CompletableFuture<FriendResult> deliverMessage(@NotNull UUID senderUuid, @NotNull UUID targetUuid,
                                                                    @NotNull String message,
                                                                    @NotNull Player targetPlayer) {
        return storage.saveLastMessageSender(targetUuid, senderUuid)
                .thenApply(ignored -> {
                    notificationService.notifyFriendMessageReceived(targetPlayer, senderUuid, message);
                    return FriendResult.MESSAGE_SENT;
                });
    }

    public @NotNull CompletableFuture<FriendResult> sendFriendReply(@NotNull UUID senderUuid, @NotNull String message) {
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

    public @NotNull CompletableFuture<FriendResult> jumpToFriend(@NotNull UUID jumperUuid, @NotNull UUID targetUuid) {
        return storage.hasFriendRelation(jumperUuid, targetUuid)
                .thenCompose(areFriends -> validateJumpPreconditions(areFriends, jumperUuid, targetUuid))
                .thenCompose(validationResult -> {
                    if (validationResult.result() != FriendResult.SUCCESS) {
                        return CompletableFuture.completedFuture(validationResult.result());
                    }
                    return executeJump(validationResult.jumper(), validationResult.target());
                });
    }

    private @NotNull CompletableFuture<JumpValidationResult> validateJumpPreconditions(boolean areFriends, @NotNull UUID jumperUuid, @NotNull UUID targetUuid) {
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

    private @NotNull CompletableFuture<FriendResult> executeJump(@NotNull Player jumper, @NotNull Player target) {
        return jumper.createConnectionRequest(target.getCurrentServer().get().getServer())
                .connect()
                .thenApply(result -> FriendResult.JUMP_SUCCESSFUL);
    }

    public @NotNull CompletableFuture<Optional<FriendSettings>> getSettings(@NotNull UUID playerUuid) {
        return storage.fetchSettings(playerUuid)
                .thenApply(settingsOpt -> settingsOpt.or(() -> Optional.of(new FriendSettings(playerUuid))));
    }

    public @NotNull CompletableFuture<Optional<Instant>> fetchLastSeen(@NotNull UUID playerUuid) {
        return storage.fetchLastSeen(playerUuid);
    }

    public @NotNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(@NotNull String username) {
        return storage.fetchPlayerByUsername(username);
    }

    // ========================================
    // SETTINGS
    // ========================================

    public @NotNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUuid(@NotNull UUID playerUuid) {
        return storage.fetchPlayerByUuid(playerUuid);
    }

    public @NotNull CompletableFuture<Optional<UUID>> fetchLastMessageSender(@NotNull UUID playerUuid) {
        return storage.fetchLastMessageSender(playerUuid);
    }

    public @NotNull CompletableFuture<FriendResult> updateSetting(@NotNull UUID playerUuid, @NotNull String setting, boolean value) {
        return storage.fetchSettings(playerUuid)
                .thenCompose(settingsOpt -> {
                    FriendSettings currentSettings = settingsOpt.orElse(new FriendSettings(playerUuid));
                    FriendSettings newSettings = applySettingUpdate(currentSettings, setting, value);

                    if (newSettings == null) {
                        return CompletableFuture.completedFuture(FriendResult.INVALID_SETTING);
                    }

                    return storage.saveSettings(playerUuid, newSettings)
                            .thenApply(ignored -> FriendResult.SETTING_UPDATED);
                });
    }

    public @NotNull CompletableFuture<FriendResult> setPresence(@NotNull UUID playerUuid, @NotNull PresenceState presenceState) {
        return storage.fetchSettings(playerUuid)
                .thenCompose(settingsOpt -> {
                    FriendSettings currentSettings = settingsOpt.orElse(new FriendSettings(playerUuid));
                    FriendSettings newSettings = currentSettings.withPresenceState(presenceState);
                    return storage.saveSettings(playerUuid, newSettings)
                            .thenApply(ignored -> FriendResult.STATUS_UPDATED);
                });
    }

    private @Nullable FriendSettings applySettingUpdate(@NotNull FriendSettings settings, @NotNull String setting, boolean value) {
        return switch (setting.toLowerCase()) {
            case "messaging" -> settings.withAllowMessages(value);
            case "jumping" -> settings.withAllowJump(value);
            case "lastseen" -> settings.withShowLastSeen(value);
            case "location" -> settings.withShowLocation(value);
            case "requests" -> settings.withAllowRequests(value);
            default -> null;
        };
    }

    public @NotNull CompletableFuture<Void> handlePlayerConnect(@NotNull UUID playerUuid, @NotNull String username) {
        return storage.upsertPlayer(playerUuid, username)
                .thenCompose(ignored -> storage.fetchFriendRelations(playerUuid))
                .thenAccept(friendRelations -> notificationService.notifyFriendsOfPlayerJoin(playerUuid, friendRelations));
    }

    public @NotNull CompletableFuture<Void> handlePlayerDisconnect(@NotNull UUID playerUuid) {
        return storage.saveLastSeen(playerUuid, Instant.now())
                .thenCompose(ignored -> storage.fetchFriendRelations(playerUuid))
                .thenAccept(friendRelations -> notificationService.notifyFriendsOfPlayerLeave(playerUuid, friendRelations));
    }

    public @NotNull CompletableFuture<Void> cleanupExpiredRequests() {
        return storage.cleanupExpiredFriendRequests(FriendProxyConstants.REQUEST_EXPIRY_DURATION);
    }

    // ========================================
    // PLAYER LIFECYCLE
    // ========================================

    public @NotNull CompletableFuture<Void> cleanupExpiredCooldowns() {
        return storage.cleanupExpiredFriendRequestCooldowns(FriendProxyConstants.COOLDOWN_EXPIRY_DURATION);
    }

    public @NotNull CompletableFuture<Void> cleanupExpiredLastMessageSenders() {
        return storage.cleanupExpiredLastMessageSenders(FriendProxyConstants.LAST_MESSAGE_SENDER_RETENTION);
    }

    // ========================================
    // CLEANUP
    // ========================================

    private @NotNull CompletableFuture<Void> removeFriendRequestPair(@NotNull UUID senderUuid, @NotNull UUID receiverUuid) {
        return storage.removeFriendRequest(senderUuid, receiverUuid)
                .thenCompose(ignoredBool -> storage.removeFriendRequest(receiverUuid, senderUuid))
                .thenApply(ignoredBool -> null);
    }

    private record PreconditionResult(FriendResult result, boolean isAlreadyFriendsOrHasOutgoing) {
    }

    private record MessageValidationResult(FriendResult result, Player targetPlayer) {
    }

    // ========================================
    // HELPERS
    // ========================================

    private record JumpValidationResult(FriendResult result, Player jumper, Player target) {
    }
}
