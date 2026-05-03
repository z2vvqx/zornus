package com.zornus.guilds.proxy.listener.player;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.zornus.guilds.proxy.service.GuildService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for player connection events.
 * Handles player upsert on login (guilds are persistent, no disconnect handling needed).
 */
public class GuildConnectionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildConnectionListener.class);

    private final @NonNull GuildService guildService;

    public GuildConnectionListener(@NonNull GuildService guildService) {
        this.guildService = guildService;
    }

    @Subscribe
    public void onPostLogin(@NonNull PostLoginEvent event) {
        Player player = event.getPlayer();
        guildService.handlePlayerJoin(player.getUniqueId(), player.getUsername())
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to handle player join for {} ({})",
                            player.getUsername(), player.getUniqueId(), throwable);
                    return null;
                });
    }
}
