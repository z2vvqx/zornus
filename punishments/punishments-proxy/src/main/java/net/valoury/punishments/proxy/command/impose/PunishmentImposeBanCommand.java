package net.valoury.punishments.proxy.command.impose;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.model.PunishmentType;
import net.valoury.punishments.proxy.model.result.PunishmentImposeResult;
import net.valoury.punishments.proxy.service.PunishmentService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class PunishmentImposeBanCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentImposeBanCommand.class);

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
                .executes(PunishmentImposeBanCommand::sendUsage)
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(PunishmentImposeBanCommand::sendUsage)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("duration_timestamp", StringArgumentType.word())
                                .executes(PunishmentImposeBanCommand::sendUsage)
                                .then(BrigadierCommand
                                        .requiredArgumentBuilder("reason_array", StringArgumentType.greedyString())
                                        .executes(context -> handleImposeBan(context, punishmentService))
                                )
                        )
                );
    }

    private static int sendUsage(@NonNull CommandContext<CommandSource> context) {
        context.getSource().sendMessage(StringUtils.deserialize(PunishmentProxyConstants.USAGE_IMPOSE_BAN));
        return Command.SINGLE_SUCCESS;
    }

    private static int handleImposeBan(@NonNull CommandContext<CommandSource> context, PunishmentService punishmentService) {
        CommandSource source = context.getSource();
        String targetName = StringArgumentType.getString(context, "player_name");
        String duration = StringArgumentType.getString(context, "duration_timestamp");
        String reason = StringArgumentType.getString(context, "reason_array");

        punishmentService.resolveTargetPlayer(targetName)
                .thenAccept(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        return;
                    }

                    PlayerRecord target = targetOptional.get();
                    punishmentService.impose(source, target, PunishmentType.BAN, duration, reason)
                            .thenAccept(result -> {
                                switch (result) {
                                    case PunishmentImposeResult.Imposed imposed -> {
                                        TagResolver resolver = TagResolver.resolver(
                                                Placeholder.unparsed("target", target.username()),
                                                Placeholder.unparsed("id", imposed.punishment().identifier().toUpperCase())
                                        );
                                        source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.IMPOSE_SUCCESS_BAN, resolver));
                                    }
                                    case PunishmentImposeResult.CannotPunishSelf ignored ->
                                            source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.ERROR_CANNOT_PUNISH_SELF));
                                    case PunishmentImposeResult.InvalidDuration ignored ->
                                            source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.ERROR_INVALID_DURATION));
                                    case PunishmentImposeResult.AlreadyBanned ignored ->
                                            source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.ERROR_PLAYER_ALREADY_BANNED,
                                                    Placeholder.unparsed("target", target.username())));
                                    case PunishmentImposeResult.PlayerNotFound ignored ->
                                            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                                    default ->
                                            source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                }
                            })
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to ban player {}", target.playerUuid(), throwable);
                                source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return null;
                            });
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve punishment target {}", targetName, throwable);
                    source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
