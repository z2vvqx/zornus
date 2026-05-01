package com.zornus.parties.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

@Plugin(id = "parties-proxy", name = "Parties Proxy", version = "1.0.0",
        url = "https://zornus.com", authors = {"Zornus"})
public final class PartyProxyPlugin {

    private final @NonNull ProxyServer proxyServer;
    private final @NonNull Logger logger;
    private PartyProxyModule partyProxyModule;

    @Inject
    public PartyProxyPlugin(@NonNull ProxyServer proxyServer, @NonNull Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(@NonNull ProxyInitializeEvent event) {
        this.partyProxyModule = new PartyProxyModule(this, proxyServer);
        partyProxyModule.initialize(proxyServer.getCommandManager(), proxyServer.getEventManager(), proxyServer.getScheduler());
        logger.info("Parties Proxy module initialized successfully");
    }

    @Subscribe
    public void onProxyShutdown(@NonNull ProxyShutdownEvent event) {
        if (partyProxyModule != null) {
            partyProxyModule.shutdown();
        }
        logger.info("Parties Proxy module shutdown complete");
    }
}

