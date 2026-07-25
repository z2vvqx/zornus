package com.zornus.punishments.proxy.command.check;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.punishments.proxy.service.PunishmentService;
import org.jspecify.annotations.NonNull;

public final class PunishmentCheckCommand {

    public static LiteralArgumentBuilder<CommandSource> create(@NonNull PunishmentService punishmentService, @NonNull ProxyServer proxyServer) {
        return BrigadierCommand.literalArgumentBuilder("check")
                .executes(PunishmentCheckHelpCommand.defaultExecutor())
                .then(PunishmentCheckHelpCommand.create())
                .then(PunishmentCheckBanCommand.create(punishmentService, proxyServer))
                .then(PunishmentCheckMuteCommand.create(punishmentService, proxyServer))
                .then(PunishmentCheckIdCommand.create(punishmentService));
    }
}
