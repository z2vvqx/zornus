package com.zornus.friends.proxy;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.friends.proxy.registrar.FriendCommandRegistrar;
import com.zornus.friends.proxy.registrar.FriendListenerRegistrar;
import com.zornus.friends.proxy.registrar.FriendOperationRegistrar;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.friends.proxy.storage.FriendPostgresStorage;
import com.zornus.friends.proxy.storage.FriendStorage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main module for friends proxy functionality.
 * Provides service coordination and initialization management.
 */
public class FriendProxyModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendProxyModule.class);

    private final @NotNull FriendService friendService;
    private final @NotNull FriendCommandRegistrar friendCommandRegistrar;
    private final @NotNull FriendListenerRegistrar friendListenerRegistrar;
    private final @NotNull FriendOperationRegistrar friendOperationRegistrar;

    /**
     * Creates a new proxy module with service initialization.
     *
     * @param plugin The plugin instance
     * @param proxyServer The proxy server instance
     */
    public FriendProxyModule(@NotNull Object plugin, @NotNull ProxyServer proxyServer) {
        FriendStorage storage = new FriendPostgresStorage(
                FriendProxyConstants.POSTGRESQL_URL,
                FriendProxyConstants.POSTGRESQL_USER,
                FriendProxyConstants.POSTGRESQL_PASSWORD
        );
        this.friendService = new FriendService(storage, proxyServer);
        this.friendCommandRegistrar = new FriendCommandRegistrar(friendService, proxyServer);
        this.friendListenerRegistrar = new FriendListenerRegistrar(plugin, friendService);
        this.friendOperationRegistrar = new FriendOperationRegistrar(plugin, storage);
    }

    /**
     * Initializes the proxy module by registering all components.
     * This operation is thread-safe and includes proper error handling.
     *
     * @param commandManager The command manager for command registration
     * @param eventManager   The event manager for listener registration
     * @param scheduler      The scheduler for task registration
     */
    public void initialize(@NotNull CommandManager commandManager, @NotNull EventManager eventManager, @NotNull Scheduler scheduler) {
        try {
            friendCommandRegistrar.registerCommands(commandManager);
            friendListenerRegistrar.registerListeners(eventManager);
            friendOperationRegistrar.registerOperations(scheduler);
        } catch (Exception exception) {
            LOGGER.error("Failed to initialize proxy module", exception);
            throw new RuntimeException("Failed to initialize proxy module", exception);
        }
    }

    /**
     * Shuts down the proxy module and closes resources.
     */
    public void shutdown() {
        try {
            friendService.closeStorage();
        } catch (Exception exception) {
            LOGGER.error("Error during proxy module shutdown", exception);
        }
    }

    /**
     * Gets the friend service.
     *
     * @return Friend service
     */
    public @NotNull FriendService getFriendService() {
        return friendService;
    }

    /**
     * Gets the command registrar.
     *
     * @return Command registrar
     */
    public @NotNull FriendCommandRegistrar getCommandRegistrar() {
        return friendCommandRegistrar;
    }

    /**
     * Gets the listener registrar.
     *
     * @return Listener registrar
     */
    public @NotNull FriendListenerRegistrar getListenerRegistrar() {
        return friendListenerRegistrar;
    }

    /**
     * Gets the operation registrar.
     *
     * @return Operation registrar
     */
    public @NotNull FriendOperationRegistrar getOperationRegistrar() {
        return friendOperationRegistrar;
    }
}
