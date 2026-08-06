package net.valoury.staff.proxy;

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
        id = "staff-proxy",
        name = "Staff Proxy",
        version = "1.0.0",
        description = "Staff connection investigation tools for Velocity",
        authors = {"valoury"}
)
public final class StaffProxyPlugin {
    private final @NonNull ProxyServer proxyServer;
    private final @NonNull Logger logger;
    private StaffProxyModule staffProxyModule;

    @Inject
    public StaffProxyPlugin(
            @NonNull ProxyServer proxyServer,
            @NonNull Logger logger
    ) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(@NonNull ProxyInitializeEvent event) {
        StaffProxyModule initializedModule = null;
        try {
            initializedModule = new StaffProxyModule(this, proxyServer);
            initializedModule.initialize(
                    proxyServer.getCommandManager(),
                    proxyServer.getEventManager(),
                    proxyServer.getScheduler()
            );
            staffProxyModule = initializedModule;
            logger.info("Staff plugin initialized successfully");
        } catch (Exception exception) {
            staffProxyModule = null;
            if (initializedModule != null) {
                initializedModule.shutdown();
            }
            logger.error("Failed to initialize Staff plugin", exception);
            proxyServer.shutdown(Component.text(
                    "Staff connections failed to initialize. Check the proxy logs."
            ));
        }
    }

    @Subscribe
    public void onProxyShutdown(@NonNull ProxyShutdownEvent event) {
        if (staffProxyModule != null) {
            staffProxyModule.shutdown();
        }
    }
}
