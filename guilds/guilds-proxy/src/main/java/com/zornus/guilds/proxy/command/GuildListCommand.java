package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.model.Guild;
import com.zornus.guilds.proxy.model.GuildResult;
import com.zornus.guilds.proxy.model.result.GuildInfoResult;
import com.zornus.guilds.proxy.model.result.GuildListResult;
import com.zornus.guilds.proxy.service.GuildService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.shared.utilities.PaginationResult;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public final class GuildListCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildListCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService,
                                                                ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("list")
                .executes(context -> handleListMembers(context, guildService, proxyServer, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> handleListMembers(
                                context, guildService, proxyServer,
                                IntegerArgumentType.getInteger(context, "page")))
                );
    }

    private static int handleListMembers(@NonNull CommandContext<CommandSource> context,
                                         GuildService guildService, ProxyServer proxyServer, int page) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        guildService.getGuildMembers(sender, page)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get guild members for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return new GuildListResult(
                            GuildResult.ERROR_ALREADY_HANDLED, PaginationResult.invalidPage(1));
                })
                .thenAccept(result -> {
                    switch (result.result()) {
                        case SUCCESS -> resolveAndDisplayMembers(
                                sender, guildService, proxyServer, result, page);
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.LIST_ERROR_NOT_IN_GUILD));
                        case INVALID_PAGE -> sender.sendMessage(StringUtils.deserialize(
                                SharedConstants.INVALID_PAGE,
                                Placeholder.unparsed("maximum_pages",
                                        String.valueOf(result.pagination().maximumPages()))));
                        case ERROR_ALREADY_HANDLED -> {
                        }
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void resolveAndDisplayMembers(@NonNull Player sender, GuildService guildService,
                                                 ProxyServer proxyServer, @NonNull GuildListResult result,
                                                 int page) {
        guildService.getGuildInfo(sender)
                .thenCombine(
                        guildService.fetchPlayersByUuids(result.pagination().items()),
                        (guildInfo, storedPlayers) -> new MemberDisplayData(guildInfo, storedPlayers))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve guild member names for player {}",
                            sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                })
                .thenAccept(displayData -> {
                    if (displayData != null) {
                        displayMembers(sender, proxyServer, result, displayData, page);
                    }
                });
    }

    private static void displayMembers(@NonNull Player sender, ProxyServer proxyServer,
                                       @NonNull GuildListResult result,
                                       @NonNull MemberDisplayData displayData, int page) {
        Guild guild = displayData.guildInfo().guild().orElse(null);
        if (guild == null) {
            sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.LIST_ERROR_NOT_IN_GUILD));
            return;
        }

        TextComponent.Builder messageBuilder = Component.text().appendNewline();
        for (UUID memberId : result.pagination().items()) {
            String memberName = proxyServer.getPlayer(memberId)
                    .map(Player::getUsername)
                    .orElseGet(() -> {
                        PlayerRecord record = displayData.storedPlayers().get(memberId);
                        return record == null ? "Unknown" : record.username();
                    });
            String format = guild.isLeader(memberId)
                    ? GuildProxyConstants.UI_LIST_MEMBER_LEADER
                    : GuildProxyConstants.UI_LIST_MEMBER_NORMAL;
            messageBuilder.append(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + format,
                    Placeholder.unparsed("member", memberName))).appendNewline();
        }

        if (result.pagination().hasMultiplePages()) {
            TagResolver resolver = TagResolver.resolver(
                    Placeholder.unparsed("current_page", String.valueOf(page)),
                    Placeholder.unparsed("maximum_pages",
                            String.valueOf(result.pagination().maximumPages()))
            );
            messageBuilder.appendNewline()
                    .append(StringUtils.deserialize(GuildProxyConstants.UI_LIST_PAGINATION, resolver));
        }
        sender.sendMessage(messageBuilder.build());
    }

    private record MemberDisplayData(
            @NonNull GuildInfoResult guildInfo,
            @NonNull Map<UUID, PlayerRecord> storedPlayers
    ) {
    }
}
