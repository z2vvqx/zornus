package com.zornus.friends.proxy.listener.player;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.zornus.friends.proxy.service.FriendService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for player connection events.
 * Handles upserting player records and tracking last seen timestamps.
 */
public class FriendConnectionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendConnectionListener.class);

    private final @NonNull FriendService friendService;

    public FriendConnectionListener(@NonNull FriendService friendService) {
        this.friendService = friendService;
    }

    @Subscribe
    public void onPostLogin(@NonNull PostLoginEvent event) {
        Player player = event.getPlayer();
        friendService.handlePlayerJoin(player.getUniqueId(), player.getUsername())
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to handle player join for {} ({})",
                            player.getUsername(), player.getUniqueId(), throwable);
                    return null;
                });
    }

    @Subscribe
    public void onDisconnect(@NonNull DisconnectEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        friendService.handlePlayerDisconnect(player.getUniqueId(), username)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to handle player disconnect for {} ({})",
                            username, player.getUniqueId(), throwable);
                    return null;
                });
    }
}
