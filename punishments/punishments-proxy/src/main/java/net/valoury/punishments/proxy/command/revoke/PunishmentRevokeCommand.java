package net.valoury.punishments.proxy.command.revoke;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.punishments.proxy.service.PunishmentService;
import org.jspecify.annotations.NonNull;

public final class PunishmentRevokeCommand {

    public static LiteralArgumentBuilder<CommandSource> create(@NonNull PunishmentService punishmentService, @NonNull ProxyServer proxyServer) {
        return BrigadierCommand.literalArgumentBuilder("revoke")
                .executes(PunishmentRevokeHelpCommand.defaultExecutor())
                .then(PunishmentRevokeHelpCommand.create())
                .then(PunishmentRevokeBanCommand.create(punishmentService, proxyServer))
                .then(PunishmentRevokeMuteCommand.create(punishmentService, proxyServer))
                .then(PunishmentRevokeIdCommand.create(punishmentService));
    }
}
