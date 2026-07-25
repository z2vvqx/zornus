package com.zornus.punishments.proxy.registrar;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.punishments.proxy.command.PunishmentCommand;
import com.zornus.punishments.proxy.service.PunishmentService;
import org.jspecify.annotations.NonNull;

public final class PunishmentCommandRegistrar {
    private final @NonNull PunishmentService punishmentService;
    private final @NonNull ProxyServer proxyServer;

    public PunishmentCommandRegistrar(
            @NonNull PunishmentService punishmentService,
            @NonNull ProxyServer proxyServer
    ) {
        this.punishmentService = punishmentService;
        this.proxyServer = proxyServer;
    }

    public void registerCommands(@NonNull CommandManager commandManager) {
        commandManager.register(
                commandManager.metaBuilder("punishment").build(),
                PunishmentCommand.create(punishmentService, proxyServer)
        );
    }
}
