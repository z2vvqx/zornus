package net.valoury.parties.proxy.registrar;

import com.velocitypowered.api.event.EventManager;
import net.valoury.parties.proxy.listener.player.PartyConnectionListener;
import net.valoury.parties.proxy.service.PartyService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartyListenerRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyListenerRegistrar.class);

    private final @NonNull Object plugin;
    private final @NonNull PartyService partyService;

    public PartyListenerRegistrar(@NonNull Object plugin, @NonNull PartyService partyService) {
        this.plugin = plugin;
        this.partyService = partyService;
    }

    public void registerListeners(@NonNull EventManager eventManager) {
        try {
            registerEventListeners(eventManager);
        } catch (Exception exception) {
            LOGGER.error("Error registering party listeners", exception);
            throw exception;
        }
    }

    private void registerEventListeners(@NonNull EventManager eventManager) {
        eventManager.register(plugin, new PartyConnectionListener(partyService));
        LOGGER.info("Registered PartyConnectionListener");
    }
}
