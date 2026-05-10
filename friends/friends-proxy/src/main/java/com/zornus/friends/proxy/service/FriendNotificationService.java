package com.zornus.friends.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.FriendRelation;
import com.zornus.friends.proxy.model.FriendSettings;
import com.zornus.friends.proxy.model.PresenceState;
import com.zornus.friends.proxy.storage.FriendStorage;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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

    public FriendNotificationService(@NonNull FriendStorage storage, @NonNull ProxyServer proxyServer) {
        this.storage = storage;
        this.proxyServer = proxyServer;
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

    public void notifyFriendMessageReceived(@NonNull Player receiver, @NonNull UUID senderUuid, @NonNull String message) {
        Optional<Player> sender = proxyServer.getPlayer(senderUuid);
        String senderName = sender.map(Player::getUsername).orElse("Unknown");

        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("sender", senderName),
                Placeholder.unparsed("message", message)
        );
        Component receivedMessage = StringUtils.deserialize(FriendProxyConstants.MESSAGE_RECEIVED_FORMAT, resolver);
        receiver.sendMessage(receivedMessage);
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
