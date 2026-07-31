package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.Guild;
import net.valoury.guilds.proxy.model.GuildRank;
import net.valoury.guilds.proxy.model.result.GuildListResult;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.PaginationResult;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GuildListCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildListCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService,
                                                               ProxyServer proxyServer) {
        return createCommand("list", guildService, proxyServer);
    }

    public static @NonNull BrigadierCommand createShortcut(GuildService guildService, ProxyServer proxyServer) {
        return new BrigadierCommand(createCommand("gl", guildService, proxyServer)
                .requires(source -> source instanceof Player));
    }

    private static LiteralArgumentBuilder<CommandSource> createCommand(
            String commandName,
            GuildService guildService,
            ProxyServer proxyServer
    ) {
        return BrigadierCommand
                .literalArgumentBuilder(commandName)
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
                .thenAccept(result -> {
                    switch (result) {
                        case GuildListResult.Found found -> resolveAndDisplayMembers(
                                sender, guildService, proxyServer, found, page);
                        case GuildListResult.NotInGuild ignored ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.LIST_ERROR_NOT_IN_GUILD));
                        case GuildListResult.InvalidPage invalidPage -> sender.sendMessage(StringUtils.deserialize(
                                SharedConstants.INVALID_PAGE,
                                Placeholder.unparsed("maximum_pages",
                                        String.valueOf(invalidPage.pagination().maximumPages()))));
                        case GuildListResult.Empty ignored ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get guild members for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void resolveAndDisplayMembers(@NonNull Player sender, GuildService guildService,
                                                 ProxyServer proxyServer, GuildListResult.Found result,
                                                 int page) {
        guildService.fetchPlayersByUuids(result.pagination().items())
                .thenAccept(storedPlayers ->
                        displayMembers(sender, proxyServer, result.pagination(), result.guild(), storedPlayers, page))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve guild member names for player {}",
                            sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
    }

    private static void displayMembers(@NonNull Player sender, ProxyServer proxyServer,
                                       @NonNull PaginationResult<UUID> pagination,
                                       @NonNull Guild guild,
                                       @NonNull Map<UUID, PlayerRecord> storedPlayers,
                                       int page) {
        TextComponent.Builder messageBuilder = Component.text().appendNewline();
        Map<GuildRank, List<Component>> entriesByRank = new EnumMap<>(GuildRank.class);
        for (UUID memberId : pagination.items()) {
            Optional<Player> onlineMember = proxyServer.getPlayer(memberId);
            String memberName = onlineMember
                    .map(Player::getUsername)
                    .orElseGet(() -> {
                        PlayerRecord record = storedPlayers.get(memberId);
                        return record == null ? "Unknown" : record.username();
                    });
            Component statusIndicator = onlineMember.isPresent()
                    ? Component.text("▲", NamedTextColor.GREEN)
                    : Component.text("▼", NamedTextColor.RED);
            GuildRank memberRank = guild.findMemberRank(memberId)
                    .orElse(GuildRank.OUTCAST);
            Component memberEntry = StringUtils.deserialize(
                    GuildProxyConstants.UI_LIST_MEMBER,
                    TagResolver.resolver(
                            Placeholder.component("status", statusIndicator),
                            Placeholder.unparsed("member", memberName)));
            entriesByRank.computeIfAbsent(memberRank, ignored -> new ArrayList<>())
                    .add(memberEntry);
        }

        boolean hasAppendedRankSection = false;
        for (GuildRank rank : GuildRank.highestFirst()) {
            List<Component> entries = entriesByRank.getOrDefault(rank, List.of());
            if (entries.isEmpty()) {
                continue;
            }

            if (hasAppendedRankSection) {
                messageBuilder.appendNewline();
            }
            messageBuilder.append(StringUtils.deserialize(
                            SharedConstants.BULLET_POINT + rankHeader(rank)))
                    .appendNewline()
                    .append(StringUtils.deserialize(SharedConstants.BULLET_POINT));
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                if (entryIndex > 0) {
                    messageBuilder.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                }
                messageBuilder.append(entries.get(entryIndex));
            }
            messageBuilder.appendNewline();
            hasAppendedRankSection = true;
        }

        if (pagination.hasMultiplePages()) {
            TagResolver resolver = TagResolver.resolver(
                    Placeholder.unparsed("current_page", String.valueOf(page)),
                    Placeholder.unparsed("maximum_pages",
                            String.valueOf(pagination.maximumPages()))
            );
            messageBuilder.appendNewline()
                    .append(StringUtils.deserialize(
                            GuildProxyConstants.UI_LIST_PAGINATION,
                            resolver
                    ))
                    .appendNewline();
        }
        sender.sendMessage(messageBuilder.build());
    }

    private static @NonNull String rankHeader(@NonNull GuildRank rank) {
        return switch (rank) {
            case LEADER -> GuildProxyConstants.UI_LIST_RANK_LEADER;
            case DIRECTOR -> GuildProxyConstants.UI_LIST_RANK_DIRECTOR;
            case OFFICER -> GuildProxyConstants.UI_LIST_RANK_OFFICER;
            case ASSOCIATE -> GuildProxyConstants.UI_LIST_RANK_ASSOCIATE;
            case OUTCAST -> GuildProxyConstants.UI_LIST_RANK_OUTCAST;
        };
    }
}
