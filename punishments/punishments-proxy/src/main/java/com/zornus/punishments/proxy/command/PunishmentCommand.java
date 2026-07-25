package com.zornus.punishments.proxy.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.punishments.proxy.command.check.PunishmentCheckCommand;
import com.zornus.punishments.proxy.command.impose.PunishmentImposeCommand;
import com.zornus.punishments.proxy.command.revoke.PunishmentRevokeCommand;
import com.zornus.punishments.proxy.service.PunishmentService;
import org.jspecify.annotations.NonNull;

public final class PunishmentCommand {

    public static @NonNull BrigadierCommand create(@NonNull PunishmentService punishmentService, @NonNull ProxyServer proxyServer) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("punishment")
                .executes(PunishmentHelpCommand.defaultExecutor())
                .then(PunishmentHelpCommand.create())
                .then(PunishmentImposeCommand.create(punishmentService, proxyServer))
                .then(PunishmentRevokeCommand.create(punishmentService, proxyServer))
                .then(PunishmentCheckCommand.create(punishmentService, proxyServer))
                .then(PunishmentHistoryCommand.create(punishmentService, proxyServer))
                .build();
        return new BrigadierCommand(node);
    }
}
