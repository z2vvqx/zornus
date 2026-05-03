package com.zornus.guilds.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

@Plugin(id = "guilds-proxy", name = "Guilds Proxy", version = "1.0.0",
        url = "https://zornus.com", authors = {"Zornus"})
public final class GuildProxyPlugin {

    private final @NonNull ProxyServer proxyServer;
    private final @NonNull Logger logger;
    private GuildProxyModule guildProxyModule;

    @Inject
    public GuildProxyPlugin(@NonNull ProxyServer proxyServer, @NonNull Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(@NonNull ProxyInitializeEvent event) {
        this.guildProxyModule = new GuildProxyModule(this, proxyServer);
        guildProxyModule.initialize(proxyServer.getCommandManager(), proxyServer.getEventManager(), proxyServer.getScheduler());
        logger.info("Guilds Proxy module initialized successfully");
    }

    @Subscribe
    public void onProxyShutdown(@NonNull ProxyShutdownEvent event) {
        if (guildProxyModule != null) {
            guildProxyModule.shutdown();
        }
        logger.info("Guilds Proxy module shutdown complete");
    }
}
