package com.zornus.parties.proxy.listener.player;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.zornus.parties.proxy.service.PartyService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for player connection events.
 * Handles party cleanup when players disconnect.
 */
public class PartyConnectionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyConnectionListener.class);

    private final @NonNull PartyService partyService;

    public PartyConnectionListener(@NonNull PartyService partyService) {
        this.partyService = partyService;
    }

    @Subscribe
    public void onDisconnect(@NonNull DisconnectEvent event) {
        Player player = event.getPlayer();
        partyService.handlePlayerDisconnect(player.getUniqueId())
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to handle player disconnect for {} ({})",
                            player.getUsername(), player.getUniqueId(), throwable);
                    return null;
                });
    }
}
