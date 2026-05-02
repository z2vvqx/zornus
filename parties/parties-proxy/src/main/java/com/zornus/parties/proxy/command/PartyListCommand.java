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
import com.zornus.parties.proxy.model.Party;
import com.zornus.parties.proxy.model.PartyResult;
import com.zornus.parties.proxy.model.result.PartyMembersResult;
import com.zornus.parties.proxy.service.PartyService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command for listing party members.
 */
public final class PartyListCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyListCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("list")
                .executes(context -> handleListMembers(context, partyService, proxyServer, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int page = IntegerArgumentType.getInteger(context, "page");
                            return handleListMembers(context, partyService, proxyServer, page);
                        })
                );
    }

    private static int handleListMembers(@NonNull CommandContext<CommandSource> context, PartyService partyService,
                                         ProxyServer proxyServer, int page) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        partyService.getPartyMembers(sender, page)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get party members for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return new PartyMembersResult(PartyResult.ERROR_ALREADY_HANDLED, null);
                })
                .thenAccept(result -> {
                    switch (result.result()) {
                        case SUCCESS -> handleDisplayPartyMembers(sender, result, partyService, proxyServer, page);
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.LIST_ERROR_NOT_IN_PARTY));
                        case INVALID_PAGE -> {
                            TagResolver resolver = Placeholder.unparsed("maximum_pages", String.valueOf(result.pagination().maximumPages()));
                            sender.sendMessage(StringUtils.deserialize(SharedConstants.INVALID_PAGE, resolver));
                        }
                        case ERROR_ALREADY_HANDLED -> {}
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleDisplayPartyMembers(Player sender, @NonNull PartyMembersResult result,
                                           PartyService partyService, ProxyServer proxyServer, int page) {
        partyService.getPlayerParty(sender.getUniqueId())
                .thenAccept(partyOptional -> {
                    if (partyOptional.isEmpty()) {
                        sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.LIST_ERROR_NOT_IN_PARTY));
                        return;
                    }

                    Party party = partyOptional.get();
                    TextComponent.Builder messageBuilder = Component.text().append(Component.newline());

                    List<UUID> members = result.pagination().items();
                    for (int i = 0; i < members.size(); i++) {
                        UUID memberId = members.get(i);

                        // Get player name from proxy server if online, otherwise use "Unknown"
                        String memberName = proxyServer.getPlayer(memberId)
                                .map(Player::getUsername)
                                .orElse("Unknown");

                        TagResolver memberResolver = Placeholder.unparsed("member", memberName);
                        String format = party.isLeader(memberId)
                                ? PartyProxyConstants.UI_LIST_MEMBER_LEADER
                                : PartyProxyConstants.UI_LIST_MEMBER_NORMAL;

                        messageBuilder.append(StringUtils.deserialize(SharedConstants.BULLET_POINT + format, memberResolver));
                        if (i < members.size() - 1) {
                            messageBuilder.append(Component.newline());
                        }
                    }

                    messageBuilder.append(Component.newline());

                    if (result.pagination().hasMultiplePages()) {
                        TagResolver paginationResolver = TagResolver.resolver(
                                Placeholder.unparsed("current_page", String.valueOf(page)),
                                Placeholder.unparsed("maximum_pages", String.valueOf(result.pagination().maximumPages()))
                        );
                        messageBuilder.append(Component.newline())
                                .append(StringUtils.deserialize(PartyProxyConstants.UI_LIST_PAGINATION, paginationResolver));
                    }

                    sender.sendMessage(messageBuilder.build());
                });
    }
}
