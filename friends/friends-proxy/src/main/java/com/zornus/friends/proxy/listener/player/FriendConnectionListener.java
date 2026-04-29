package com.zornus.friends.proxy.listener.player;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.zornus.friends.proxy.service.FriendService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for player connection events.
 * Handles upserting player records and tracking last seen timestamps.
 */
public class FriendConnectionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendConnectionListener.class);

    private final @NotNull FriendService friendService;

    public FriendConnectionListener(@NotNull FriendService friendService) {
        this.friendService = friendService;
    }

    @Subscribe
    public void onPostLogin(@NotNull PostLoginEvent event) {
        Player player = event.getPlayer();
        friendService.handlePlayerConnect(player.getUniqueId(), player.getUsername())
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to handle player join for {} ({})",
                            player.getUsername(), player.getUniqueId(), throwable);
                    return null;
                });
    }

    @Subscribe
    public void onDisconnect(@NotNull DisconnectEvent event) {
        Player player = event.getPlayer();
        friendService.handlePlayerDisconnect(player.getUniqueId())
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to handle player disconnect for {} ({})",
                            player.getUsername(), player.getUniqueId(), throwable);
                    return null;
                });
    }
}
