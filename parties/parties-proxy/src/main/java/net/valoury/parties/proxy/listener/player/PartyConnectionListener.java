package net.valoury.parties.proxy.listener.player;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.valoury.parties.proxy.service.PartyService;
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
    public @NonNull EventTask onDisconnect(@NonNull DisconnectEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        return EventTask.resumeWhenComplete(
                partyService.handlePlayerDisconnect(player.getUniqueId(), username)
                        .exceptionally(throwable -> {
                            LOGGER.error("Failed to handle player disconnect for {} ({})",
                                    username, player.getUniqueId(), throwable);
                            return null;
                        })
        );
    }
}
