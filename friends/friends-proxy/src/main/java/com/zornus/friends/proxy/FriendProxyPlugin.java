package com.zornus.friends.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

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

    private final @NonNull ProxyServer proxyServer;
    private final @NonNull Logger logger;
    private FriendProxyModule friendProxyModule;

    @Inject
    public FriendProxyPlugin(@NonNull ProxyServer proxyServer, @NonNull Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(@NonNull ProxyInitializeEvent event) {
        try {
            logger.info("Initializing Friends plugin...");

            this.friendProxyModule = new FriendProxyModule(this, proxyServer);
            friendProxyModule.initialize(
                    proxyServer.getCommandManager(),
                    proxyServer.getEventManager(),
                    proxyServer.getScheduler()
            );

            logger.info("Friends plugin initialized successfully");
        } catch (Exception exception) {
            logger.error("Failed to initialize Friends plugin", exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(@NonNull ProxyShutdownEvent event) {
        try {
            logger.info("Shutting down Friends plugin...");

            if (friendProxyModule != null) {
                friendProxyModule.shutdown();
            }

            logger.info("Friends plugin shut down successfully");
        } catch (Exception exception) {
            logger.error("Error during Friends plugin shutdown", exception);
        }
    }

    public FriendProxyModule getFriendProxyModule() {
        return friendProxyModule;
    }
}
