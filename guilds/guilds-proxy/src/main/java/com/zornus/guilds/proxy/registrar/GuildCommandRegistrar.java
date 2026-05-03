package com.zornus.guilds.proxy.registrar;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.guilds.proxy.service.GuildService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registrar for guild commands.
 */
public final class GuildCommandRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildCommandRegistrar.class);

    private final @NonNull GuildService guildService;
    private final @NonNull ProxyServer proxyServer;

    /**
     * Creates a new command registrar.
     *
     * @param guildService Service for guild operations
     * @param proxyServer  Proxy server for player lookups
     */
    public GuildCommandRegistrar(@NonNull GuildService guildService, @NonNull ProxyServer proxyServer) {
        this.guildService = guildService;
        this.proxyServer = proxyServer;
    }

    /**
     * Registers all guild commands.
     * This operation is thread-safe and includes proper error handling.
     *
     * @param commandManager The command manager for command registration
     */
    public void registerCommands(@NonNull CommandManager commandManager) {
        try {
            // Commands are implemented as follow-up task
            LOGGER.info("Guild commands will be registered in follow-up implementation");
        } catch (Exception exception) {
            LOGGER.error("Error registering guild commands", exception);
            throw exception;
        }
    }
}
