package net.valoury.punishments.proxy.command.impose;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.service.PunishmentService;
import net.valoury.punishments.proxy.service.PresetEvidenceCoordinator;
import org.jspecify.annotations.NonNull;

public final class PunishmentImposeCommand {

    public static LiteralArgumentBuilder<CommandSource> create(
            @NonNull PunishmentService punishmentService,
            @NonNull ProxyServer proxyServer,
            @NonNull PresetEvidenceCoordinator evidenceCoordinator
    ) {
        return BrigadierCommand.literalArgumentBuilder("impose")
                .requires(source -> source.hasPermission(
                        PunishmentProxyConstants.IMPOSE_COMMAND_PERMISSION
                ))
                .executes(PunishmentImposeHelpCommand.defaultExecutor())
                .then(PunishmentImposeHelpCommand.create())
                .then(PunishmentImposeBanCommand.create(punishmentService, proxyServer))
                .then(PunishmentImposeMuteCommand.create(punishmentService, proxyServer))
                .then(PunishmentImposeWarnCommand.create(punishmentService, proxyServer))
                .then(PunishmentImposeKickCommand.create(punishmentService, proxyServer))
                .then(PunishmentImposePresetCommand.create(punishmentService, proxyServer, evidenceCoordinator));
    }
}
