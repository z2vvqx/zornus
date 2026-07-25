package com.zornus.parties.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.friends.api.FriendsApi;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

@Plugin(id = "parties-proxy", name = "Parties Proxy", version = "1.0.0",
        url = "https://zornus.com", authors = {"Zornus"},
        dependencies = {@Dependency(id = FriendsApi.PLUGIN_ID)})
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
    public void onProxyInitialize(@NonNull ProxyInitializeEvent event) {
        try {
            logger.info("Initializing Parties plugin...");

            FriendsApi friendsApi = resolveFriendsApi();
            this.partyProxyModule = new PartyProxyModule(this, proxyServer, friendsApi.friendships());
            partyProxyModule.initialize(proxyServer.getCommandManager(), proxyServer.getEventManager(), proxyServer.getScheduler());
            logger.info("Parties plugin initialized successfully");
        } catch (Exception exception) {
            logger.error("Failed to initialize Parties plugin", exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(@NonNull ProxyShutdownEvent event) {
        try {
            logger.info("Shutting down Parties plugin...");

            if (partyProxyModule != null) {
                partyProxyModule.shutdown();
            }

            logger.info("Parties plugin shut down successfully");
        } catch (Exception exception) {
            logger.error("Error during Parties plugin shutdown", exception);
        }
    }

    private @NonNull FriendsApi resolveFriendsApi() {
        Object friendsPlugin = proxyServer.getPluginManager()
                .getPlugin(FriendsApi.PLUGIN_ID)
                .flatMap(pluginContainer -> pluginContainer.getInstance())
                .orElseThrow(() -> new IllegalStateException("Friends plugin instance is unavailable"));

        if (!(friendsPlugin instanceof FriendsApi friendsApi)) {
            throw new IllegalStateException("Friends plugin does not expose the expected API");
        }
        return friendsApi;
    }
}

