package com.zornus.parties.proxy.registrar;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.parties.proxy.service.PartyService;
import org.jspecify.annotations.NonNull;

public final class PartyCommandRegistrar {

    private final @NonNull PartyService partyService;
    private final @NonNull ProxyServer proxyServer;

    public PartyCommandRegistrar(@NonNull PartyService partyService, @NonNull ProxyServer proxyServer) {
        this.partyService = partyService;
        this.proxyServer = proxyServer;
    }

    public void registerCommands(@NonNull CommandManager commandManager) {
    }
}
