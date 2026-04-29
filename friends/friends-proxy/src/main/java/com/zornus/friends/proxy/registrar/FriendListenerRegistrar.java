package com.zornus.friends.proxy.registrar;

import com.velocitypowered.api.event.EventManager;
import com.zornus.friends.proxy.listener.player.FriendConnectionListener;
import com.zornus.friends.proxy.service.FriendService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registrar for friend event listeners.
 */
public class FriendListenerRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendListenerRegistrar.class);

    private final @NotNull Object plugin;
    private final @NotNull FriendService friendService;

    /**
     * Creates a new listener registrar.
     *
     * @param plugin The plugin instance for event registration
     * @param friendService The friend service for listener dependencies
     */
    public FriendListenerRegistrar(@NotNull Object plugin, @NotNull FriendService friendService) {
        this.plugin = plugin;
        this.friendService = friendService;
    }

    /**
     * Registers all friend event listeners.
     * This operation is thread-safe and includes proper error handling.
     *
     * @param eventManager The event manager for listener registration
     */
    public void registerListeners(@NotNull EventManager eventManager) {
        try {
            registerEventListeners(eventManager);
        } catch (Exception exception) {
            LOGGER.error("Error registering friend listeners", exception);
            throw exception;
        }
    }

    /**
     * Registers all event listeners with the event manager.
     *
     * @param eventManager The event manager for listener registration
     */
    private void registerEventListeners(@NotNull EventManager eventManager) {
        eventManager.register(plugin, new FriendConnectionListener(friendService));
        LOGGER.info("Registered FriendConnectionListener");
    }
}
