package net.valoury.punishments.proxy.command.revoke;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.model.PunishmentType;
import net.valoury.punishments.proxy.model.result.PunishmentRevokeResult;
import net.valoury.punishments.proxy.service.PunishmentService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class PunishmentRevokeBanCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentRevokeBanCommand.class);

    private static SuggestionProvider<CommandSource> onlinePlayerSuggestions(ProxyServer proxyServer) {
        return (context, builder) -> {
            String remainingInput = builder.getRemainingLowerCase();
            if (remainingInput.isEmpty()) {
                return builder.buildFuture();
            }
            proxyServer.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(username -> username.toLowerCase(Locale.ROOT).startsWith(remainingInput))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    public static LiteralArgumentBuilder<CommandSource> create(PunishmentService punishmentService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("ban")
                .requires(source -> source.hasPermission(
                        PunishmentProxyConstants.REVOKE_BAN_COMMAND_PERMISSION
                ))
                .executes(PunishmentRevokeBanCommand::sendUsage)
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(PunishmentRevokeBanCommand::sendUsage)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("reason_array", StringArgumentType.greedyString())
                                .executes(context -> handleRevokeBan(context, punishmentService))
                        )
                );
    }

    private static int sendUsage(@NonNull CommandContext<CommandSource> context) {
        context.getSource().sendMessage(StringUtils.deserialize(PunishmentProxyConstants.USAGE_REVOKE_BAN));
        return Command.SINGLE_SUCCESS;
    }

    private static int handleRevokeBan(@NonNull CommandContext<CommandSource> context, PunishmentService punishmentService) {
        CommandSource source = context.getSource();
        String targetName = StringArgumentType.getString(context, "player_name");
        String reason = StringArgumentType.getString(context, "reason_array");

        punishmentService.resolveTargetPlayer(targetName)
                .thenAccept(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        return;
                    }

                    PlayerRecord target = targetOptional.get();
                    punishmentService.revokeActive(target, PunishmentType.BAN, source, reason)
                            .thenAccept(result -> {
                                switch (result) {
                                    case PunishmentRevokeResult.Revoked ignored ->
                                            source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.REVOKE_SUCCESS_BAN,
                                                    Placeholder.unparsed("target", target.username())));
                                    case PunishmentRevokeResult.PlayerNotBanned ignored ->
                                            source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.PLAYER_NOT_BANNED,
                                                    Placeholder.unparsed("target", target.username())));
                                    default ->
                                            source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                }
                            })
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to revoke ban for {}", target.playerUuid(), throwable);
                                source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return null;
                            });
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve revocation target {}", targetName, throwable);
                    source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
