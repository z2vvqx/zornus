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
import com.zornus.guilds.proxy.model.GuildInvitation;
import com.zornus.guilds.proxy.model.result.GuildRequestsResult;
import com.zornus.guilds.proxy.service.GuildService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class GuildRequestsCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildRequestsCommand.class);

    private GuildRequestsCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService,
                                                                ProxyServer proxyServer) {
        return BrigadierCommand.literalArgumentBuilder("requests")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_REQUESTS));
                    return Command.SINGLE_SUCCESS;
                })
                .then(createDirection("incoming", guildService, proxyServer))
                .then(createDirection("outgoing", guildService, proxyServer));
    }

    private static LiteralArgumentBuilder<CommandSource> createDirection(String direction,
                                                                          GuildService guildService,
                                                                          ProxyServer proxyServer) {
        return BrigadierCommand.literalArgumentBuilder(direction)
                .executes(context -> handleRequests(context, guildService, proxyServer, direction, 1))
                .then(BrigadierCommand.requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> handleRequests(context, guildService, proxyServer, direction,
                                IntegerArgumentType.getInteger(context, "page"))));
    }

    private static int handleRequests(CommandContext<CommandSource> context, GuildService guildService,
                                      ProxyServer proxyServer, String direction, int page) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        guildService.getRequestsList(sender.getUniqueId(), direction, page)
                .thenAccept(result -> handleResult(
                        sender, guildService, proxyServer, result, direction, page))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get guild requests for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }

    private static void handleResult(Player sender, GuildService guildService, ProxyServer proxyServer,
                                     GuildRequestsResult result, String direction, int page) {
        switch (result) {
            case GuildRequestsResult.Empty ignored -> sender.sendMessage(StringUtils.deserialize(
                    "incoming".equals(direction)
                            ? GuildProxyConstants.UI_REQUESTS_INCOMING_EMPTY
                            : GuildProxyConstants.UI_REQUESTS_OUTGOING_EMPTY));
            case GuildRequestsResult.InvalidPage invalidPage -> sender.sendMessage(StringUtils.deserialize(
                    SharedConstants.INVALID_PAGE,
                    Placeholder.unparsed("maximum_pages",
                            String.valueOf(invalidPage.pagination().maximumPages()))));
            case GuildRequestsResult.InvalidRequestType ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
            case GuildRequestsResult.Found found ->
                    resolveAndDisplayRequests(sender, guildService, proxyServer, found, direction, page);
        }
    }

    private static void resolveAndDisplayRequests(
            Player sender,
            GuildService guildService,
            ProxyServer proxyServer,
            GuildRequestsResult.Found result,
            String direction,
            int page
    ) {
        resolveNames(guildService, proxyServer, result.pagination().items(), direction)
                .exceptionally(throwable -> {
                        LOGGER.error("Failed to resolve guild invitation names", throwable);
                        sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                        return null;
                })
                .thenAccept(names -> {
                    if (names != null) {
                        display(sender, result, direction, page, names);
                    }
                });
    }

    private static CompletableFuture<Map<GuildInvitation, String>> resolveNames(
            GuildService guildService, ProxyServer proxyServer,
            List<GuildInvitation> invitations, String direction) {
        if ("outgoing".equals(direction)) {
            List<UUID> targetIds = invitations.stream().map(GuildInvitation::targetId).toList();
            return guildService.fetchPlayersByUuids(targetIds)
                    .thenApply(players -> invitations.stream().collect(Collectors.toUnmodifiableMap(
                            Function.identity(),
                            invitation -> {
                                var onlinePlayer = proxyServer.getPlayer(invitation.targetId());
                                if (onlinePlayer.isPresent()) {
                                    return onlinePlayer.get().getUsername();
                                }
                                PlayerRecord record = players.get(invitation.targetId());
                                return record == null ? "Unknown" : record.username();
                            })));
        }

        List<CompletableFuture<ResolvedInvitation>> resolutions = invitations.stream()
                .map(invitation -> guildService.fetchGuild(invitation.guildId())
                        .thenApply(guild -> new ResolvedInvitation(invitation,
                                guild.map(value -> value.guildName()).orElse("Unknown"))))
                .toList();
        return CompletableFuture.allOf(resolutions.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> resolutions.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toUnmodifiableMap(
                                ResolvedInvitation::invitation, ResolvedInvitation::name)));
    }

    private static void display(Player sender, GuildRequestsResult.Found result, String direction, int page,
                                Map<GuildInvitation, String> names) {
        boolean incoming = "incoming".equals(direction);
        String template = incoming
                ? GuildProxyConstants.UI_REQUESTS_INCOMING_ENTRY
                : GuildProxyConstants.UI_REQUESTS_OUTGOING_ENTRY;
        List<Component> entries = result.pagination().items().stream()
                .map(invitation -> {
                    String name = names.getOrDefault(invitation, "Unknown");
                    TagResolver resolver = incoming
                            ? TagResolver.resolver(
                                    Placeholder.unparsed("guild_name", name),
                                    Placeholder.component("timestamp",
                                            StringUtils.formatRelativeTime(invitation.timestamp())))
                            : TagResolver.resolver(
                                    Placeholder.unparsed("player", name),
                                    Placeholder.component("timestamp",
                                            StringUtils.formatRelativeTime(invitation.timestamp())));
                    return StringUtils.deserialize(SharedConstants.BULLET_POINT + template, resolver);
                })
                .toList();

        TextComponent.Builder message = Component.text().appendNewline()
                .append(Component.join(JoinConfiguration.newlines(), entries))
                .appendNewline();
        if (result.pagination().hasMultiplePages()) {
            message.appendNewline().append(StringUtils.deserialize(GuildProxyConstants.UI_REQUESTS_PAGINATION,
                    TagResolver.resolver(
                            Placeholder.unparsed("current_page", String.valueOf(page)),
                            Placeholder.unparsed("maximum_pages",
                                    String.valueOf(result.pagination().maximumPages())),
                            Placeholder.unparsed("type", direction))));
        }
        sender.sendMessage(message.build());
    }

    private record ResolvedInvitation(GuildInvitation invitation, String name) {
    }
}
