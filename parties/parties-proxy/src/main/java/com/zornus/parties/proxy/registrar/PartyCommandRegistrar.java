package com.zornus.parties.proxy.registrar;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.parties.proxy.command.PartyCommand;
import com.zornus.parties.proxy.service.PartyService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registrar for party commands.
 */
public final class PartyCommandRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyCommandRegistrar.class);

    private final @NonNull PartyService partyService;
    private final @NonNull ProxyServer proxyServer;

    /**
     * Creates a new command registrar.
     *
     * @param partyService Service for party operations
     * @param proxyServer  Proxy server for player lookups
     */
    public PartyCommandRegistrar(@NonNull PartyService partyService, @NonNull ProxyServer proxyServer) {
        this.partyService = partyService;
        this.proxyServer = proxyServer;
    }

    /**
     * Registers all party commands.
     * This operation is thread-safe and includes proper error handling.
     *
     * @param commandManager The command manager for command registration
     */
    public void registerCommands(@NonNull CommandManager commandManager) {
        try {
            registerPartyCommand(commandManager);
        } catch (Exception exception) {
            LOGGER.error("Error registering party commands", exception);
            throw exception;
        }
    }

    /**
     * Registers the main party command with all subcommands.
     *
     * @param commandManager The command manager for command registration
     */
    private void registerPartyCommand(@NonNull CommandManager commandManager) {
        commandManager.register(commandManager.metaBuilder("party").build(), PartyCommand.create(partyService, proxyServer));
    }
}
