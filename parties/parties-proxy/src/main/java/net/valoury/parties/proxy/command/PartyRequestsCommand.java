package net.valoury.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.PartyInvitation;
import net.valoury.parties.proxy.model.result.PartyRequestsResult;
import net.valoury.parties.proxy.service.PartyService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.PaginationResult;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Command for viewing pending party invitations with pagination.
 */
public final class PartyRequestsCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyRequestsCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("requests")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PartyProxyConstants.USAGE_REQUESTS));
                    return Command.SINGLE_SUCCESS;
                })
                .then(createTypeBranch("incoming", partyService, proxyServer))
                .then(createTypeBranch("outgoing", partyService, proxyServer));
    }

    private static LiteralArgumentBuilder<CommandSource> createTypeBranch(String type, PartyService partyService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder(type)
                .executes(context -> handleRequests(context, partyService, proxyServer, type, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int page = IntegerArgumentType.getInteger(context, "page");
                            return handleRequests(context, partyService, proxyServer, type, page);
                        })
                );
    }

    private static int handleRequests(@NonNull CommandContext<CommandSource> context, PartyService partyService,
                                      ProxyServer proxyServer, @NonNull String type, int page) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        partyService.getRequestsList(sender.getUniqueId(), type, page)
                .thenAccept(result -> {
                    switch (result) {
                        case PartyRequestsResult.Empty ignored -> {
                            String emptyMessage = type.equalsIgnoreCase("incoming")
                                    ? PartyProxyConstants.UI_REQUESTS_INCOMING_EMPTY
                                    : PartyProxyConstants.UI_REQUESTS_OUTGOING_EMPTY;
                            sender.sendMessage(StringUtils.deserialize(emptyMessage));
                        }
                        case PartyRequestsResult.InvalidPage invalidPage -> {
                            TagResolver pageResolver = TagResolver.resolver(
                                    Placeholder.unparsed("maximum_pages", String.valueOf(invalidPage.pagination().maximumPages()))
                            );
                            sender.sendMessage(StringUtils.deserialize(SharedConstants.INVALID_PAGE, pageResolver));
                        }
                        case PartyRequestsResult.Found found ->
                                handleDisplayRequestsPage(sender, found.pagination(), type, page, proxyServer);
                        case PartyRequestsResult.InvalidRequestType ignored ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get party requests for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleDisplayRequestsPage(
            @NonNull Player sender,
            @NonNull PaginationResult<PartyInvitation> pagination,
            @NonNull String type,
            int currentPage,
            ProxyServer proxyServer
    ) {
        TextComponent.Builder messageBuilder = Component.text().appendNewline();

        boolean isIncoming = type.equalsIgnoreCase("incoming");
        String entryFormat = isIncoming
                ? PartyProxyConstants.UI_REQUESTS_INCOMING_ENTRY
                : PartyProxyConstants.UI_REQUESTS_OUTGOING_ENTRY;

        List<Component> invitationEntries = new ArrayList<>();
        for (PartyInvitation invitation : pagination.items()) {
            UUID playerId = isIncoming ? invitation.senderId() : invitation.targetId();
            String playerName = getPlayerName(proxyServer, playerId);
            Component timestampComponent = StringUtils.formatRelativeTime(invitation.timestamp());

            invitationEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + entryFormat,
                    TagResolver.resolver(
                            Placeholder.parsed("player", StringUtils.escapeTags(playerName)),
                            Placeholder.component("timestamp", timestampComponent)
                    )
            ));
        }

        messageBuilder.append(Component.join(JoinConfiguration.newlines(), invitationEntries));
        messageBuilder.append(Component.newline());

        if (pagination.hasMultiplePages()) {
            messageBuilder.append(Component.newline())
                    .append(StringUtils.deserialize(PartyProxyConstants.UI_REQUESTS_PAGINATION,
                            TagResolver.resolver(
                                    Placeholder.unparsed("current_page", String.valueOf(currentPage)),
                                    Placeholder.unparsed("maximum_pages", String.valueOf(pagination.maximumPages())),
                                    Placeholder.unparsed("type", type)
                            )
                    ))
                    .append(Component.newline());
        }

        sender.sendMessage(messageBuilder.build());
    }

    private static String getPlayerName(ProxyServer proxyServer, UUID playerId) {
        return proxyServer.getPlayer(playerId)
                .map(Player::getUsername)
                .orElse("Unknown");
    }
}
