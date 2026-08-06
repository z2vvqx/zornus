package net.valoury.punishments.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.model.Punishment;
import net.valoury.punishments.proxy.model.result.PunishmentHistoryResult;
import net.valoury.punishments.proxy.service.PunishmentService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.PaginationResult;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PunishmentHistoryCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentHistoryCommand.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern(PunishmentProxyConstants.HISTORY_DATE_FORMAT)
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
        RequiredArgumentBuilder<CommandSource, String> player = BrigadierCommand
                .requiredArgumentBuilder("player_name", StringArgumentType.word())
                .suggests(onlinePlayerSuggestions(proxyServer))
                .executes(context -> handleHistory(context, punishmentService, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> handleHistory(context, punishmentService,
                                IntegerArgumentType.getInteger(context, "page")))
                );

        return BrigadierCommand.literalArgumentBuilder("history")
                .requires(source -> source.hasPermission(
                        PunishmentProxyConstants.HISTORY_COMMAND_PERMISSION
                ))
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PunishmentProxyConstants.USAGE_HISTORY));
                    return Command.SINGLE_SUCCESS;
                })
                .then(player);
    }

    private static int handleHistory(CommandContext<CommandSource> context, PunishmentService punishmentService, int page) {
        CommandSource source = context.getSource();
        String targetName = StringArgumentType.getString(context, "player_name");

        punishmentService.resolveTargetPlayer(targetName)
                .thenAccept(target -> {
                    if (target.isEmpty()) {
                        source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        return;
                    }

                    PlayerRecord targetRecord = target.get();
                    punishmentService.fetchHistory(targetRecord.playerUuid())
                            .thenAccept(result -> displayHistory(source, targetRecord.username(), result, page))
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to fetch punishment history for {}", targetRecord.playerUuid(), throwable);
                                source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return null;
                            });
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve punishment history target {}", targetName, throwable);
                    source.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void displayHistory(CommandSource source, String targetName, PunishmentHistoryResult result, int page) {
        if (result instanceof PunishmentHistoryResult.Empty) {
            source.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.UI_HISTORY_EMPTY));
            return;
        }
        List<Punishment> punishments = ((PunishmentHistoryResult.Found) result).punishments();
        PaginationResult<Punishment> pagination = PaginationResult.paginate(punishments, page, SharedConstants.ENTRIES_PER_PAGE);
        if (!pagination.isValidPage()) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.INVALID_PAGE,
                    Placeholder.unparsed("maximum_pages", String.valueOf(pagination.maximumPages()))));
            return;
        }

        List<Component> entries = new ArrayList<>();
        for (Punishment punishment : pagination.items()) {
            String indicator = punishment.active()
                    ? PunishmentProxyConstants.UI_HISTORY_INDICATOR_ACTIVE
                    : punishment.revokingPlayerId() != null
                    ? PunishmentProxyConstants.UI_HISTORY_INDICATOR_REVOKED
                    : PunishmentProxyConstants.UI_HISTORY_INDICATOR_EXPIRED;
            entries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + PunishmentProxyConstants.UI_HISTORY_ENTRY,
                    TagResolver.resolver(
                            Placeholder.parsed("indicator", indicator),
                            Placeholder.unparsed("date", DATE_FORMATTER.format(punishment.createdAt())),
                            Placeholder.unparsed("id", punishment.identifier().toUpperCase()),
                            Placeholder.unparsed("type", punishment.type().toString()),
                            Placeholder.unparsed("reason", punishment.reason()))));
        }
        TextComponent.Builder message = Component.text()
                .appendNewline()
                .append(Component.join(JoinConfiguration.newlines(), entries));
        if (pagination.hasMultiplePages()) {
            message.appendNewline().appendNewline().append(StringUtils.deserialize(
                    PunishmentProxyConstants.UI_HISTORY_PAGINATION,
                    TagResolver.resolver(
                            Placeholder.unparsed("current_page", String.valueOf(page)),
                            Placeholder.unparsed("maximum_pages", String.valueOf(pagination.maximumPages())),
                            Placeholder.unparsed("target", targetName))));
        }
        source.sendMessage(message.appendNewline().build());
    }
}
