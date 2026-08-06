package net.valoury.punishments.proxy.command.check;

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
import net.valoury.punishments.proxy.model.result.PunishmentCheckResult;
import net.valoury.punishments.proxy.service.PunishmentService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PunishmentCheckBanCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentCheckBanCommand.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern(PunishmentProxyConstants.CHECK_DATE_FORMAT)
            .withZone(ZoneId.systemDefault());

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
                        PunishmentProxyConstants.CHECK_BAN_COMMAND_PERMISSION
                ))
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PunishmentProxyConstants.USAGE_CHECK_BAN));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleCheckBan(context, punishmentService))
                );
    }

    private static int handleCheckBan(@NonNull CommandContext<CommandSource> context, PunishmentService punishmentService) {
        CommandSource source = context.getSource();
        String targetName = StringArgumentType.getString(context, "player_name");

        punishmentService.resolveTargetPlayer(targetName)
                .thenAccept(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        return;
                    }

                    PlayerRecord target = targetOptional.get();
                    punishmentService.check(target.playerUuid(), PunishmentType.BAN)
                            .thenAccept(result -> {
                                switch (result) {
                                    case PunishmentCheckResult.PlayerNotBanned ignored ->
                                            source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.PLAYER_NOT_BANNED,
                                                    Placeholder.unparsed("target", target.username())));
                                    case PunishmentCheckResult.Found found -> {
                                        String expires = found.punishment().expiresAt() == null
                                                ? PunishmentProxyConstants.PERMANENT
                                                : DATE_FORMATTER.format(found.punishment().expiresAt());
                                        TagResolver resolver = TagResolver.resolver(
                                                Placeholder.unparsed("target", target.username()),
                                                Placeholder.unparsed("expires", expires),
                                                Placeholder.unparsed("reason", found.punishment().reason()),
                                                Placeholder.unparsed("id", found.punishment().identifier().toUpperCase())
                                        );
                                        source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.CHECK_PLAYER_BANNED, resolver));
                                    }
                                    default ->
                                            source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                }
                            })
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to check ban for {}", target.playerUuid(), throwable);
                                source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return null;
                            });
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve ban check target {}", targetName, throwable);
                    source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
