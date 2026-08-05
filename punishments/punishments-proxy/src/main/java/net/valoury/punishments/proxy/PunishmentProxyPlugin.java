package net.valoury.punishments.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

@Plugin(
        id = "punishments-proxy",
        name = "Punishments Proxy",
        version = "1.0.0",
        description = "Network-wide punishment system for Velocity",
        authors = {"valoury"}
)
public final class PunishmentProxyPlugin {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private PunishmentProxyModule punishmentProxyModule;

    @Inject
    public PunishmentProxyPlugin(@NonNull ProxyServer proxyServer, @NonNull Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(@NonNull ProxyInitializeEvent event) {
        PunishmentProxyModule initializedModule = null;
        try {
            initializedModule = new PunishmentProxyModule(this, proxyServer);
            initializedModule.initialize(
                    proxyServer.getCommandManager(),
                    proxyServer.getEventManager(),
                    proxyServer.getScheduler());
            punishmentProxyModule = initializedModule;
            logger.info("Punishments plugin initialized successfully");
        } catch (Exception exception) {
            punishmentProxyModule = null;
            if (initializedModule != null) {
                initializedModule.shutdown();
            }
            logger.error("Failed to initialize Punishments plugin", exception);
            proxyServer.shutdown(Component.text(
                    "Punishments failed to initialize. The proxy is shutting down to prevent unenforced punishments."));
        }
    }

    @Subscribe
    public void onProxyShutdown(@NonNull ProxyShutdownEvent event) {
        if (punishmentProxyModule != null) {
            punishmentProxyModule.shutdown();
        }
    }
}
