package net.valoury.guilds.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.valoury.friends.api.FriendsApi;
import net.luckperms.api.LuckPermsProvider;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

@Plugin(id = "guilds-proxy", name = "Guilds Proxy", version = "1.0.0",
        url = "https://valoury.com", authors = {"valoury"},
        dependencies = {
                @Dependency(id = FriendsApi.PLUGIN_ID),
                @Dependency(id = "luckperms")
        })
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
    public void onProxyInitialize(@NonNull ProxyInitializeEvent event) {
        GuildProxyModule initializedModule = null;
        try {
            logger.info("Initializing Guilds plugin...");

            FriendsApi friendsApi = resolveFriendsApi();
            initializedModule = new GuildProxyModule(
                    this,
                    proxyServer,
                    friendsApi.friendships(),
                    LuckPermsProvider.get()
            );
            initializedModule.initialize(
                    proxyServer.getCommandManager(),
                    proxyServer.getEventManager(),
                    proxyServer.getScheduler()
            );
            this.guildProxyModule = initializedModule;
            logger.info("Guilds plugin initialized successfully");
        } catch (Exception exception) {
            guildProxyModule = null;
            if (initializedModule != null) {
                initializedModule.shutdown();
            }
            logger.error("Failed to initialize Guilds plugin", exception);
            proxyServer.shutdown(Component.text("Guilds failed to initialize. Check the proxy logs."));
        }
    }

    @Subscribe
    public void onProxyShutdown(@NonNull ProxyShutdownEvent event) {
        try {
            logger.info("Shutting down Guilds plugin...");

            if (guildProxyModule != null) {
                guildProxyModule.shutdown();
            }

            logger.info("Guilds plugin shut down successfully");
        } catch (Exception exception) {
            logger.error("Error during Guilds plugin shutdown", exception);
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
