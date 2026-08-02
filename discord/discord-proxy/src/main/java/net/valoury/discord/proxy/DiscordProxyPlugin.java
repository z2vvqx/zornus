package net.valoury.discord.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.discord.api.DiscordApi;
import net.valoury.discord.api.link.AccountLinkService;
import org.slf4j.Logger;

@Plugin(
        id = "discord-proxy",
        name = "Discord Proxy",
        version = "1.0.0",
        description = "Secure Minecraft and Discord account linking",
        authors = {"valoury"}
)
public final class DiscordProxyPlugin implements DiscordApi {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private DiscordProxyModule discordProxyModule;

    @Inject
    public DiscordProxyPlugin(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            discordProxyModule = new DiscordProxyModule();
            discordProxyModule.initialize(proxyServer.getCommandManager());
            logger.info("Discord account linking initialized successfully");
        } catch (RuntimeException exception) {
            logger.error("Failed to initialize Discord account linking", exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (discordProxyModule == null) {
            return;
        }
        try {
            discordProxyModule.close();
            logger.info("Discord account linking shut down successfully");
        } catch (RuntimeException exception) {
            logger.error("Failed to shut down Discord account linking", exception);
        }
    }

    @Override
    public AccountLinkService accountLinks() {
        if (discordProxyModule == null) {
            throw new IllegalStateException("Discord API is unavailable before plugin initialization");
        }
        return discordProxyModule.accountLinks();
    }
}
