package com.zornus.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.PartyInvitation;
import com.zornus.parties.proxy.model.PartyResult;
import com.zornus.parties.proxy.model.result.PartyRequestsResult;
import com.zornus.parties.proxy.service.PartyService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get party requests for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                })
                .thenAccept(result -> {
                    if (result == null) return;
                    switch (result.result()) {
                        case LIST_EMPTY -> {
                            String emptyMessage = type.equalsIgnoreCase("incoming")
                                    ? PartyProxyConstants.UI_REQUESTS_INCOMING_EMPTY
                                    : PartyProxyConstants.UI_REQUESTS_OUTGOING_EMPTY;
                            sender.sendMessage(StringUtils.deserialize(emptyMessage));
                        }
                        case INVALID_PAGE -> {
                            TagResolver pageResolver = TagResolver.resolver(
                                    Placeholder.unparsed("maximum_pages", String.valueOf(result.pagination().maximumPages()))
                            );
                            sender.sendMessage(StringUtils.deserialize(SharedConstants.INVALID_PAGE, pageResolver));
                        }
                        case SUCCESS -> displayRequestsPage(sender, result, type, page, proxyServer);
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void displayRequestsPage(@NonNull Player sender, @NonNull PartyRequestsResult result,
                                           @NonNull String type, int currentPage, ProxyServer proxyServer) {
        TextComponent.Builder messageBuilder = Component.text().appendNewline();

        boolean isIncoming = type.equalsIgnoreCase("incoming");
        String entryFormat = isIncoming
                ? PartyProxyConstants.UI_REQUESTS_INCOMING_ENTRY
                : PartyProxyConstants.UI_REQUESTS_OUTGOING_ENTRY;

        List<Component> invitationEntries = new ArrayList<>();
        for (PartyInvitation invitation : result.pagination().items()) {
            UUID playerId = isIncoming ? invitation.senderId() : invitation.targetId();
            String playerName = getPlayerName(proxyServer, playerId);
            Component timestampComponent = StringUtils.formatRelativeTime(invitation.timestamp());

            invitationEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + entryFormat,
                    TagResolver.resolver(
                            Placeholder.parsed("player", playerName),
                            Placeholder.component("timestamp", timestampComponent)
                    )
            ));
        }

        messageBuilder.append(Component.join(JoinConfiguration.newlines(), invitationEntries));
        messageBuilder.append(Component.newline());

        if (result.pagination().hasMultiplePages()) {
            messageBuilder.append(Component.newline())
                    .append(StringUtils.deserialize(PartyProxyConstants.UI_REQUESTS_PAGINATION,
                            TagResolver.resolver(
                                    Placeholder.unparsed("current_page", String.valueOf(currentPage)),
                                    Placeholder.unparsed("maximum_pages", String.valueOf(result.pagination().maximumPages())),
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
