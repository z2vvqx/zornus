package net.valoury.friends.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.friends.proxy.FriendProxyConstants;
import net.valoury.friends.proxy.model.FriendRelation;
import net.valoury.friends.proxy.model.FriendSettings;
import net.valoury.friends.proxy.model.PresenceState;
import net.valoury.friends.proxy.storage.FriendStorage;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.LuckPerms;
import net.valoury.shared.utilities.PlayerNameFormatter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class FriendNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendNotificationService.class);

    private final @NonNull FriendStorage storage;
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull LuckPerms luckPerms;

    public FriendNotificationService(
            @NonNull FriendStorage storage,
            @NonNull ProxyServer proxyServer,
            @NonNull LuckPerms luckPerms
    ) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.luckPerms = luckPerms;
    }

    public void notifyFriendsOfPlayerJoin(@NonNull UUID joiningPlayerUuid, @NonNull String username,
                                          @NonNull List<FriendRelation> friendRelations) {
        storage.fetchSettings(joiningPlayerUuid)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to fetch settings for player {}", joiningPlayerUuid, throwable);
                    return Optional.empty();
                })
                .thenAccept(settingsOptional -> {
                    FriendSettings settings = settingsOptional.orElse(new FriendSettings(joiningPlayerUuid));
                    if (settings.presenceState() == PresenceState.OFFLINE) {
                        return;
                    }

                    List<Player> onlineFriends = collectOnlineFriends(joiningPlayerUuid, friendRelations);
                    if (onlineFriends.isEmpty()) {
                        return;
                    }

                    TagResolver resolver = TagResolver.resolver(Placeholder.unparsed("friend", username));
                    Component joinMessage = StringUtils.deserialize(FriendProxyConstants.NOTIFICATION_FRIEND_JOINED, resolver);

                    for (Player friend : onlineFriends) {
                        friend.sendMessage(joinMessage);
                    }
                });
    }

    public void notifyFriendsOfPlayerLeave(@NonNull UUID leavingPlayerUuid, @NonNull String username,
                                           @NonNull List<FriendRelation> friendRelations) {
        storage.fetchSettings(leavingPlayerUuid)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to fetch settings for player {}", leavingPlayerUuid, throwable);
                    return Optional.empty();
                })
                .thenAccept(settingsOptional -> {
                    FriendSettings settings = settingsOptional.orElse(new FriendSettings(leavingPlayerUuid));
                    if (settings.presenceState() == PresenceState.OFFLINE) {
                        return;
                    }

                    List<Player> onlineFriends = collectOnlineFriends(leavingPlayerUuid, friendRelations);
                    if (onlineFriends.isEmpty()) {
                        return;
                    }

                    TagResolver resolver = TagResolver.resolver(Placeholder.unparsed("friend", username));
                    Component leaveMessage = StringUtils.deserialize(FriendProxyConstants.NOTIFICATION_FRIEND_LEFT, resolver);

                    for (Player friend : onlineFriends) {
                        friend.sendMessage(leaveMessage);
                    }
                });
    }

    public void notifyFriendRequestReceived(@NonNull UUID receiverUuid, @NonNull UUID senderUuid) {
        Optional<Player> receiver = proxyServer.getPlayer(receiverUuid);
        if (receiver.isEmpty()) {
            return;
        }

        String senderUsername = proxyServer.getPlayer(senderUuid)
                .map(Player::getUsername)
                .orElse("Unknown");

        TagResolver resolver = TagResolver.resolver(Placeholder.parsed("sender", StringUtils.escapeTags(senderUsername)));
        Component message = StringUtils.deserialize(FriendProxyConstants.NOTIFICATION_REQUEST_RECEIVED, resolver);
        receiver.get().sendMessage(message);
    }

    public void notifyFriendRequestAccepted(@NonNull UUID targetUuid, @NonNull UUID otherPlayerUuid) {
        Optional<Player> targetPlayer = proxyServer.getPlayer(targetUuid);
        if (targetPlayer.isEmpty()) {
            return;
        }

        String otherName = proxyServer.getPlayer(otherPlayerUuid)
                .map(Player::getUsername)
                .orElse("Unknown");

        TagResolver resolver = TagResolver.resolver(Placeholder.unparsed("sender", otherName));
        Component message = StringUtils.deserialize(FriendProxyConstants.NOTIFICATION_REQUEST_ACCEPTED, resolver);
        targetPlayer.get().sendMessage(message);
    }

    public void notifyIncomingFriendRequests(@NonNull UUID playerId, int incomingRequestCount) {
        if (incomingRequestCount <= 0) {
            return;
        }

        proxyServer.getPlayer(playerId).ifPresent(player -> {
            TagResolver resolver = TagResolver.resolver(
                    Placeholder.unparsed("request_count", String.valueOf(incomingRequestCount)),
                    Placeholder.unparsed("request_noun", incomingRequestCount == 1 ? "request" : "requests")
            );
            player.sendMessage(StringUtils.deserialize(FriendProxyConstants.NOTIFICATION_INCOMING_REQUESTS, resolver));
        });
    }

    public void notifyFriendMessageReceived(@NonNull Player receiver, @NonNull UUID senderUuid, @NonNull String message) {
        TagResolver resolver = TagResolver.resolver(
                Placeholder.component("sender", resolveOnlinePlayerName(senderUuid, "Unknown")),
                Placeholder.unparsed("message", message)
        );
        Component receivedMessage = StringUtils.deserialize(FriendProxyConstants.MESSAGE_RECEIVED_FORMAT, resolver);
        receiver.sendMessage(receivedMessage);
    }

    public @NonNull Component resolveOnlinePlayerName(
            @NonNull UUID playerId,
            @NonNull String fallbackUsername
    ) {
        return proxyServer.getPlayer(playerId)
                .map(this::formatPlayerName)
                .orElseGet(() -> Component.text(fallbackUsername));
    }

    private @NonNull Component formatPlayerName(@NonNull Player player) {
        Component username = Component.text(player.getUsername());
        try {
            String suffix = luckPerms.getPlayerAdapter(Player.class)
                    .getMetaData(player)
                    .getSuffix();
            return PlayerNameFormatter.formatSuffixBeforeName(suffix, username);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to resolve LuckPerms suffix for {}; using username without suffix",
                    player.getUniqueId(),
                    exception
            );
            return username;
        }
    }

    private @NonNull List<Player> collectOnlineFriends(@NonNull UUID playerId, @NonNull List<FriendRelation> friendRelations) {
        List<Player> onlineFriends = new ArrayList<>(friendRelations.size());
        for (FriendRelation friendRelation : friendRelations) {
            UUID friendId = friendRelation.getOtherPlayerUuid(playerId);
            proxyServer.getPlayer(friendId).ifPresent(onlineFriends::add);
        }
        return onlineFriends;
    }
}
