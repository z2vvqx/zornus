package net.valoury.guilds.proxy.registrar;

import com.velocitypowered.api.event.EventManager;
import net.valoury.guilds.proxy.listener.player.GuildConnectionListener;
import net.valoury.guilds.proxy.service.GuildService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildListenerRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildListenerRegistrar.class);

    private final @NonNull Object plugin;
    private final @NonNull GuildService guildService;

    public GuildListenerRegistrar(@NonNull Object plugin, @NonNull GuildService guildService) {
        this.plugin = plugin;
        this.guildService = guildService;
    }

    public void registerListeners(@NonNull EventManager eventManager) {
        try {
            registerEventListeners(eventManager);
        } catch (Exception exception) {
            LOGGER.error("Error registering guild listeners", exception);
            throw exception;
        }
    }

    private void registerEventListeners(@NonNull EventManager eventManager) {
        eventManager.register(plugin, new GuildConnectionListener(guildService));
        LOGGER.info("Registered GuildConnectionListener");
    }
}
