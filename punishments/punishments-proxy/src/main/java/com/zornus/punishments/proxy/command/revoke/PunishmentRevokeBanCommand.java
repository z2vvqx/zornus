package com.zornus.punishments.proxy.command.revoke;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.punishments.proxy.PunishmentProxyConstants;
import com.zornus.punishments.proxy.model.PunishmentType;
import com.zornus.punishments.proxy.model.result.PunishmentRevokeResult;
import com.zornus.punishments.proxy.service.PunishmentService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.shared.utilities.StringUtils;
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
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PunishmentProxyConstants.USAGE_REVOKE_BAN));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("reason_array", StringArgumentType.greedyString())
                                .executes(context -> handleRevokeBan(context, punishmentService))
                        )
                );
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
                                    default -> source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
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
