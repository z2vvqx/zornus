package net.valoury.punishments.proxy.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.punishments.proxy.command.check.PunishmentCheckCommand;
import net.valoury.punishments.proxy.command.impose.PunishmentImposeCommand;
import net.valoury.punishments.proxy.command.revoke.PunishmentRevokeCommand;
import net.valoury.punishments.proxy.service.PunishmentService;
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
