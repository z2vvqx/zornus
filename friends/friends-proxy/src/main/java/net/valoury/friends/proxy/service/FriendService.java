package net.valoury.friends.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.valoury.friends.api.FriendshipService;
import net.valoury.friends.proxy.FriendProxyConstants;
import net.valoury.friends.proxy.model.*;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.friends.proxy.model.result.*;
import net.valoury.friends.proxy.storage.AcceptRequestOutcome;
import net.valoury.friends.proxy.storage.FriendStorage;
import net.valoury.friends.proxy.storage.SendRequestOutcome;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FriendService implements FriendshipService, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendService.class);

    private final @NonNull FriendStorage storage;
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull FriendNotificationService notificationService;

    public FriendService(
            @NonNull FriendStorage storage,
            @NonNull ProxyServer proxyServer,
            @NonNull LuckPerms luckPerms
    ) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.notificationService = new FriendNotificationService(storage, proxyServer, luckPerms);
    }

    @Override
    public void close() {
        storage.close();
    }


    public @NonNull FriendNotificationService getNotificationService() {
        return notificationService;
    }

    public @NonNull Component resolveOnlinePlayerName(
            @NonNull UUID playerId,
            @NonNull String fallbackUsername
    ) {
        return notificationService.resolveOnlinePlayerName(playerId, fallbackUsername);
    }

    public @NonNull CompletableFuture<SendFriendRequestResult> sendFriendRequest(
            @NonNull UUID senderUuid,
            @NonNull UUID targetUuid
    ) {
        if (senderUuid.equals(targetUuid)) {
            return CompletableFuture.completedFuture(new SendFriendRequestResult.CannotAddSelf());
        }

        return storage.trySendFriendRequest(senderUuid, targetUuid)
                .thenApply(outcome -> switch (outcome) {
                    case SendRequestOutcome.Sent sent -> {
                        notificationService.notifyFriendRequestReceived(targetUuid, senderUuid);
                        yield new SendFriendRequestResult.Sent();
                    }
                    case SendRequestOutcome.RequestAcceptedAutomatically auto -> {
                        notificationService.notifyFriendRequestAccepted(targetUuid, senderUuid);
                        notificationService.notifyFriendRequestAccepted(senderUuid, targetUuid);
                        yield new SendFriendRequestResult.AcceptedAutomatically();
                    }
                    case SendRequestOutcome.AlreadyFriends ignored -> new SendFriendRequestResult.AlreadyFriends();
                    case SendRequestOutcome.RequestAlreadySent ignored -> new SendFriendRequestResult.AlreadySent();
                    case SendRequestOutcome.SenderRequestLimitReached ignored ->
                            new SendFriendRequestResult.SenderRequestLimitReached();
                    case SendRequestOutcome.ReceiverRequestLimitReached ignored ->
                            new SendFriendRequestResult.ReceiverRequestLimitReached();
                    case SendRequestOutcome.SenderFriendsLimitReached ignored ->
                            new SendFriendRequestResult.SenderFriendLimitReached();
                    case SendRequestOutcome.ReceiverFriendsLimitReached ignored ->
                            new SendFriendRequestResult.ReceiverFriendLimitReached();
                    case SendRequestOutcome.RequestCooldownActive ignored ->
                            new SendFriendRequestResult.CooldownActive();
                    case SendRequestOutcome.PlayerNotAcceptingRequests ignored ->
                            new SendFriendRequestResult.ReceiverNotAcceptingRequests();
                    case SendRequestOutcome.RequestNoLongerValid ignored ->
                            new SendFriendRequestResult.RequestNoLongerValid();
                });
    }

    public @NonNull CompletableFuture<AcceptFriendRequestResult> acceptFriendRequest(
            @NonNull UUID accepterUuid,
            @NonNull UUID requesterUuid
    ) {
        return storage.acceptFriendRequest(accepterUuid, requesterUuid)
                .thenApply(outcome -> switch (outcome) {
                    case AcceptRequestOutcome.Accepted accepted -> {
                        notificationService.notifyFriendRequestAccepted(requesterUuid, accepterUuid);
                        yield new AcceptFriendRequestResult.Accepted();
                    }
                    case AcceptRequestOutcome.NoRequestFound ignored -> new AcceptFriendRequestResult.NoRequestFound();
                    case AcceptRequestOutcome.AlreadyFriends ignored -> new AcceptFriendRequestResult.AlreadyFriends();
                    case AcceptRequestOutcome.AccepterFriendsLimitReached ignored ->
                            new AcceptFriendRequestResult.AccepterFriendLimitReached();
                    case AcceptRequestOutcome.RequesterFriendsLimitReached ignored ->
                            new AcceptFriendRequestResult.RequesterFriendLimitReached();
                });
    }

    public @NonNull CompletableFuture<RejectFriendRequestResult> rejectFriendRequest(
            @NonNull UUID rejecterUuid,
            @NonNull UUID requesterUuid
    ) {
        return storage.removeFriendRequest(requesterUuid, rejecterUuid)
                .thenApply(removed -> removed
                        ? new RejectFriendRequestResult.Rejected()
                        : new RejectFriendRequestResult.NoRequestFound());
    }

    public @NonNull CompletableFuture<RevokeFriendRequestResult> revokeFriendRequest(
            @NonNull UUID revokerUuid,
            @NonNull UUID targetUuid
    ) {
        return storage.removeFriendRequest(revokerUuid, targetUuid)
                .thenApply(removed -> removed
                        ? new RevokeFriendRequestResult.Revoked()
                        : new RevokeFriendRequestResult.NoRequestFound());
    }

    public @NonNull CompletableFuture<FriendRequestListResult> getIncomingRequestsList(@NonNull UUID playerUuid, int page) {
        return storage.fetchIncomingFriendRequests(playerUuid)
                .thenApply(requests -> {
                    if (requests.isEmpty()) {
                        return new FriendRequestListResult.Empty();
                    }
                    PaginationResult<FriendRequest> pagination = PaginationResult.paginate(requests, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new FriendRequestListResult.InvalidPage(pagination);
                    }
                    return new FriendRequestListResult.Found(pagination);
                });
    }

    public @NonNull CompletableFuture<FriendRequestListResult> getOutgoingRequestsList(@NonNull UUID playerUuid, int page) {
        return storage.fetchOutgoingFriendRequests(playerUuid)
                .thenApply(requests -> {
                    if (requests.isEmpty()) {
                        return new FriendRequestListResult.Empty();
                    }
                    PaginationResult<FriendRequest> pagination = PaginationResult.paginate(requests, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new FriendRequestListResult.InvalidPage(pagination);
                    }
                    return new FriendRequestListResult.Found(pagination);
                });
    }

    public @NonNull CompletableFuture<RemoveFriendResult> removeFriend(
            @NonNull UUID removerUuid,
            @NonNull UUID friendUuid
    ) {
        return storage.removeFriendRelation(removerUuid, friendUuid)
                .thenApply(removed -> removed
                        ? new RemoveFriendResult.Removed()
                        : new RemoveFriendResult.NotFriends());
    }

    @Override
    public @NonNull CompletableFuture<Boolean> areFriends(
            @NonNull UUID firstPlayerId,
            @NonNull UUID secondPlayerId
    ) {
        return storage.hasFriendRelation(firstPlayerId, secondPlayerId);
    }

    public @NonNull CompletableFuture<FriendListResult> getFriendsList(@NonNull UUID playerUuid, int page) {
        return storage.fetchFriendRelations(playerUuid)
                .thenApply(relations -> {
                    if (relations.isEmpty()) {
                        return new FriendListResult.Empty();
                    }
                    PaginationResult<FriendRelation> pagination = PaginationResult.paginate(relations, page, SharedConstants.ENTRIES_PER_PAGE);
                    if (!pagination.isValidPage()) {
                        return new FriendListResult.InvalidPage(pagination);
                    }
                    return new FriendListResult.Found(pagination);
                });
    }

    public @NonNull CompletableFuture<SendFriendMessageResult> sendFriendMessage(
            @NonNull UUID senderUuid,
            @NonNull UUID targetUuid,
            @NonNull String message
    ) {
        if (message.length() > FriendProxyConstants.MAX_MESSAGE_LENGTH) {
            return CompletableFuture.completedFuture(new SendFriendMessageResult.MessageTooLong());
        }

        return storage.hasFriendRelation(senderUuid, targetUuid)
                .thenCompose(areFriends -> validateMessagePreconditions(areFriends, targetUuid))
                .thenCompose(validationResult -> {
                    return switch (validationResult) {
                        case MessageValidationResult.Ready ready ->
                                deliverMessage(senderUuid, targetUuid, message, ready.targetPlayer());
                        case MessageValidationResult.Rejected rejected ->
                                CompletableFuture.completedFuture(rejected.result());
                    };
                });
    }

    private @NonNull CompletableFuture<MessageValidationResult> validateMessagePreconditions(
            boolean areFriends,
            @NonNull UUID targetUuid
    ) {
        if (!areFriends) {
            return CompletableFuture.completedFuture(
                    new MessageValidationResult.Rejected(new SendFriendMessageResult.NotFriends()));
        }
        return storage.fetchSettings(targetUuid)
                .thenApply(settingsOpt -> {
                    FriendSettings settings = settingsOpt.orElse(new FriendSettings(targetUuid));
                    if (!settings.allowMessages()) {
                        return new MessageValidationResult.Rejected(
                                new SendFriendMessageResult.ReceiverNotAcceptingMessages());
                    }
                    Optional<Player> targetPlayer = proxyServer.getPlayer(targetUuid);
                    if (targetPlayer.isEmpty()) {
                        return new MessageValidationResult.Rejected(
                                new SendFriendMessageResult.FriendNotOnline());
                    }
                    return new MessageValidationResult.Ready(targetPlayer.get());
                });
    }

    private @NonNull CompletableFuture<SendFriendMessageResult> deliverMessage(
            @NonNull UUID senderUuid,
            @NonNull UUID targetUuid,
            @NonNull String message,
            @NonNull Player targetPlayer
    ) {
        return storage.saveLastMessageSender(targetUuid, senderUuid)
                .thenApply(ignored -> {
                    notificationService.notifyFriendMessageReceived(targetPlayer, senderUuid, message);
                    return new SendFriendMessageResult.Sent();
                });
    }

    public @NonNull CompletableFuture<FriendReplyResult> sendFriendReply(@NonNull UUID senderUuid, @NonNull String message) {
        if (message.length() > FriendProxyConstants.MAX_MESSAGE_LENGTH) {
            return CompletableFuture.completedFuture(new FriendReplyResult.MessageTooLong());
        }

        return storage.fetchLastMessageSender(senderUuid)
                .thenCompose(lastSenderOpt -> {
                    if (lastSenderOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(new FriendReplyResult.NoRecentMessage());
                    }
                    UUID targetUuid = lastSenderOpt.get();
                    return resolvePlayerName(targetUuid)
                            .thenCompose(targetName -> sendFriendMessageWithValidation(senderUuid, targetUuid, message, targetName));
                });
    }

    private @NonNull CompletableFuture<String> resolvePlayerName(@NonNull UUID playerUuid) {
        return proxyServer.getPlayer(playerUuid)
                .map(player -> CompletableFuture.completedFuture(player.getUsername()))
                .orElseGet(() -> storage.fetchPlayerByUuid(playerUuid)
                        .thenApply(recordOpt -> recordOpt
                                .map(PlayerRecord::username)
                                .orElse("Unknown")));
    }

    private @NonNull CompletableFuture<FriendReplyResult> sendFriendMessageWithValidation(@NonNull UUID senderUuid,
                                                                                          @NonNull UUID targetUuid,
                                                                                          @NonNull String message,
                                                                                          @NonNull String targetName) {
        return storage.hasFriendRelation(senderUuid, targetUuid)
                .thenCompose(areFriends -> {
                    if (!areFriends) {
                        return CompletableFuture.completedFuture(new FriendReplyResult.NotFriends(targetName));
                    }
                    return storage.fetchSettings(targetUuid)
                            .thenCompose(settingsOpt -> {
                                FriendSettings settings = settingsOpt.orElse(new FriendSettings(targetUuid));
                                if (!settings.allowMessages()) {
                                    return CompletableFuture.completedFuture(new FriendReplyResult.PlayerNotAcceptingMessages(targetName));
                                }
                                Optional<Player> targetPlayer = proxyServer.getPlayer(targetUuid);
                                if (targetPlayer.isEmpty()) {
                                    return CompletableFuture.completedFuture(new FriendReplyResult.FriendNotOnline(targetName));
                                }
                                return deliverMessage(senderUuid, targetUuid, message, targetPlayer.get())
                                        .thenApply(result -> new FriendReplyResult.Success(targetUuid, targetName));
                            });
                });
    }

    public @NonNull CompletableFuture<JumpToFriendResult> jumpToFriend(
            @NonNull UUID jumperUuid,
            @NonNull UUID targetUuid
    ) {
        return storage.hasFriendRelation(jumperUuid, targetUuid)
                .thenCompose(areFriends -> validateJumpPreconditions(areFriends, jumperUuid, targetUuid))
                .thenCompose(validationResult -> {
                    return switch (validationResult) {
                        case JumpValidationResult.Ready ready -> executeJump(ready.jumper(), ready.target());
                        case JumpValidationResult.Rejected rejected ->
                                CompletableFuture.completedFuture(rejected.result());
                    };
                });
    }

    private @NonNull CompletableFuture<JumpValidationResult> validateJumpPreconditions(
            boolean areFriends,
            @NonNull UUID jumperUuid,
            @NonNull UUID targetUuid
    ) {
        if (!areFriends) {
            return CompletableFuture.completedFuture(
                    new JumpValidationResult.Rejected(new JumpToFriendResult.NotFriends()));
        }
        return storage.fetchSettings(targetUuid)
                .thenApply(settingsOpt -> {
                    FriendSettings settings = settingsOpt.orElse(new FriendSettings(targetUuid));
                    if (!settings.allowJump()) {
                        return new JumpValidationResult.Rejected(
                                new JumpToFriendResult.TargetNotAllowingJump());
                    }

                    Optional<Player> targetPlayer = proxyServer.getPlayer(targetUuid);
                    if (targetPlayer.isEmpty()) {
                        return new JumpValidationResult.Rejected(
                                new JumpToFriendResult.FriendNotOnline());
                    }

                    Player target = targetPlayer.get();
                    Optional<Player> jumper = proxyServer.getPlayer(jumperUuid);
                    if (jumper.isEmpty()) {
                        return new JumpValidationResult.Rejected(
                                new JumpToFriendResult.PlayerNotOnline());
                    }

                    if (target.getCurrentServer().isEmpty()) {
                        return new JumpValidationResult.Rejected(
                                new JumpToFriendResult.FriendHasNoInstance());
                    }

                    String targetServer = target.getCurrentServer().get().getServerInfo().getName();
                    Optional<String> jumperServer = jumper.get().getCurrentServer().map(s -> s.getServerInfo().getName());

                    if (jumperServer.isPresent() && jumperServer.get().equals(targetServer)) {
                        return new JumpValidationResult.Rejected(
                                new JumpToFriendResult.AlreadyInSameInstance());
                    }

                    return new JumpValidationResult.Ready(jumper.get(), target);
                });
    }

    private @NonNull CompletableFuture<JumpToFriendResult> executeJump(
            @NonNull Player jumper,
            @NonNull Player target
    ) {
        Optional<Player> currentTarget = proxyServer.getPlayer(target.getUniqueId());
        if (currentTarget.isEmpty() || currentTarget.get().getCurrentServer().isEmpty()) {
            return CompletableFuture.completedFuture(new JumpToFriendResult.FriendNotOnline());
        }
        Player actualTarget = currentTarget.get();
        return jumper.createConnectionRequest(actualTarget.getCurrentServer().get().getServer())
                .connect()
                .<JumpToFriendResult>thenApply(result -> new JumpToFriendResult.Jumped())
                .exceptionally(throwable -> new JumpToFriendResult.JumpFailed());
    }

    public @NonNull CompletableFuture<FriendSettings> getSettings(@NonNull UUID playerUuid) {
        return storage.fetchSettings(playerUuid)
                .thenApply(settingsOpt -> settingsOpt.orElse(new FriendSettings(playerUuid)));
    }

    public @NonNull CompletableFuture<Optional<Instant>> fetchLastSeen(@NonNull UUID playerUuid) {
        return storage.fetchLastSeen(playerUuid);
    }

    public @NonNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(@NonNull String username) {
        return storage.fetchPlayerByUsername(username);
    }

    public @NonNull CompletableFuture<Optional<PlayerRecord>> resolveTargetPlayer(@NonNull String username) {
        Optional<Player> onlinePlayer = proxyServer.getPlayer(username);
        if (onlinePlayer.isPresent()) {
            Player player = onlinePlayer.get();
            return CompletableFuture.completedFuture(
                    Optional.of(new PlayerRecord(player.getUniqueId(), player.getUsername())));
        }
        return storage.fetchPlayerByUsername(username);
    }

    public @NonNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUuid(@NonNull UUID playerUuid) {
        return storage.fetchPlayerByUuid(playerUuid);
    }

    public @NonNull CompletableFuture<Optional<UUID>> fetchLastMessageSender(@NonNull UUID playerUuid) {
        return storage.fetchLastMessageSender(playerUuid);
    }

    public @NonNull CompletableFuture<Duration> getRemainingRequestCooldown(@NonNull UUID senderId, @NonNull UUID receiverId) {
        return storage.fetchFriendRequestCooldown(senderId, receiverId)
                .thenApply(lastOptional -> {
                    if (lastOptional.isEmpty()) {
                        return Duration.ZERO;
                    }
                    Instant lastTimestamp = lastOptional.get();
                    Instant expiryTime = lastTimestamp.plus(FriendProxyConstants.FRIEND_REQUEST_COOLDOWN);
                    Duration remaining = Duration.between(Instant.now(), expiryTime);
                    return remaining.isNegative() ? Duration.ZERO : remaining;
                });
    }

    public @NonNull CompletableFuture<UpdateFriendSettingResult> updateSetting(
            @NonNull UUID playerUuid,
            @NonNull String setting,
            boolean value
    ) {
        return applySettingUpdateAtomic(playerUuid, setting, value)
                .thenApply(updated -> updated
                        ? new UpdateFriendSettingResult.Updated()
                        : new UpdateFriendSettingResult.InvalidSetting());
    }

    public @NonNull CompletableFuture<SetPresenceResult> setPresence(
            @NonNull UUID playerUuid,
            @NonNull PresenceState presenceState
    ) {
        return storage.updatePresenceState(playerUuid, presenceState)
                .thenApply(ignored -> new SetPresenceResult.Updated());
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

    public @NonNull CompletableFuture<Void> handlePlayerJoin(@NonNull UUID playerUuid, @NonNull String username) {
        return storage.upsertPlayer(playerUuid, username)
                .thenCompose(ignored -> CompletableFuture.allOf(
                        storage.fetchFriendRelations(playerUuid)
                                .thenAccept(friendRelations -> notificationService.notifyFriendsOfPlayerJoin(
                                        playerUuid,
                                        username,
                                        friendRelations
                                )),
                        storage.countIncomingFriendRequests(playerUuid)
                                .thenAccept(incomingRequestCount -> notificationService.notifyIncomingFriendRequests(
                                        playerUuid,
                                        incomingRequestCount
                                ))
                ));
    }

    public @NonNull CompletableFuture<Void> handlePlayerDisconnect(@NonNull UUID playerUuid, @NonNull String username) {
        if (proxyServer.getPlayer(playerUuid).isPresent()) {
            return CompletableFuture.completedFuture(null);
        }
        return storage.saveLastSeenIfPresenceOnline(playerUuid, Instant.now())
                .thenCompose(lastSeenUpdated -> {
                    if (!lastSeenUpdated) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return storage.fetchFriendRelations(playerUuid)
                            .thenAccept(friendRelations -> notificationService.notifyFriendsOfPlayerLeave(
                                    playerUuid,
                                    username,
                                    friendRelations
                            ));
                });
    }

    public void cleanupExpiredRequests() {
        storage.cleanupExpiredFriendRequests(Instant.now(), FriendProxyConstants.REQUEST_EXPIRY_DURATION)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to cleanup expired friend requests", throwable);
                    return null;
                });
    }

    public void cleanupExpiredCooldowns() {
        storage.cleanupExpiredFriendRequestCooldowns(Instant.now(), FriendProxyConstants.FRIEND_REQUEST_COOLDOWN)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to cleanup expired friend request cooldowns", throwable);
                    return null;
                });
    }

    public void cleanupExpiredLastMessageSenders() {
        storage.cleanupExpiredLastMessageSenders(Instant.now(), FriendProxyConstants.LAST_MESSAGE_SENDER_RETENTION)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to cleanup expired last message senders", throwable);
                    return null;
                });
    }

    private sealed interface MessageValidationResult {
        record Ready(@NonNull Player targetPlayer) implements MessageValidationResult {
        }

        record Rejected(@NonNull SendFriendMessageResult result) implements MessageValidationResult {
        }
    }

    private sealed interface JumpValidationResult {
        record Ready(@NonNull Player jumper, @NonNull Player target) implements JumpValidationResult {
        }

        record Rejected(@NonNull JumpToFriendResult result) implements JumpValidationResult {
        }
    }
}
