package com.zornus.friends.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Velocity plugin for friends system.
 */
@Plugin(
        id = "friends",
        name = "Friends",
        version = "1.0.0",
        description = "Friend system for Velocity proxy",
        authors = {"Zornus"}
)
public class FriendProxyPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendProxyPlugin.class);

    private final @NonNull ProxyServer proxyServer;
    private final @NonNull FriendProxyModule friendProxyModule;

    @Inject
    public FriendProxyPlugin(@NonNull ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
        this.friendProxyModule = new FriendProxyModule(this, proxyServer);
    }

    @Subscribe
    public void onProxyInitialize(@NonNull ProxyInitializeEvent event) {
        try {
            LOGGER.info("Initializing Friends plugin...");

            friendProxyModule.initialize(
                    proxyServer.getCommandManager(),
                    proxyServer.getEventManager(),
                    proxyServer.getScheduler()
            );

            LOGGER.info("Friends plugin initialized successfully");
        } catch (Exception exception) {
            LOGGER.error("Failed to initialize Friends plugin", exception);
            throw exception;
        }
    }

    @Subscribe
    public void onProxyShutdown(@NonNull ProxyShutdownEvent event) {
        try {
            LOGGER.info("Shutting down Friends plugin...");

            friendProxyModule.shutdown();

            LOGGER.info("Friends plugin shut down successfully");
        } catch (Exception exception) {
            LOGGER.error("Error during Friends plugin shutdown", exception);
        }
    }

    public @NonNull FriendProxyModule getFriendProxyModule() {
        return friendProxyModule;
    }
}
