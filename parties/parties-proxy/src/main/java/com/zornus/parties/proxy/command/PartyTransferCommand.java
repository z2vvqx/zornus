package com.zornus.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.PartyResult;
import com.zornus.parties.proxy.service.PartyService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Command for transferring party leadership.
 */
public final class PartyTransferCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyTransferCommand.class);

    private static final SuggestionProvider<CommandSource> PLAYER_SUGGESTIONS = (context, builder) -> {
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("transfer")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PartyProxyConstants.USAGE_TRANSFER));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("member_name", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(context -> handleTransferLeadership(context, partyService, proxyServer, false))
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("confirmation", StringArgumentType.word())
                                .executes(context -> {
                                    String confirmation = StringArgumentType.getString(context, "confirmation");
                                    return handleTransferLeadership(context, partyService, proxyServer, "confirm".equalsIgnoreCase(confirmation));
                                })
                        )
                );
    }

    private static int handleTransferLeadership(@NonNull CommandContext<CommandSource> context, PartyService partyService,
                                                ProxyServer proxyServer, boolean isConfirming) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "member_name");

        Optional<Player> targetOptional = proxyServer.getPlayer(targetName);
        if (targetOptional.isEmpty()) {
            sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            return Command.SINGLE_SUCCESS;
        }
        Player target = targetOptional.get();

        partyService.transferLeadership(sender, target, isConfirming)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to transfer leadership from {} to {}", sender.getUniqueId(), target.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.TRANSFER_ERROR_NOT_IN_PARTY));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_LEADER));
                        case CANNOT_TRANSFER_TO_SELF ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.TRANSFER_ERROR_CANNOT_TRANSFER_SELF));
                        case PLAYER_NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.TRANSFER_ERROR_PLAYER_NOT_IN_PARTY,
                                        Placeholder.unparsed("target", targetName)));
                        case TRANSFER_CONFIRMATION_REQUIRED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.TRANSFER_CONFIRMATION_REQUIRED,
                                        Placeholder.unparsed("target", targetName)));
                        case NO_CONFIRMATION_PENDING ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.TRANSFER_ERROR_NO_CONFIRMATION));
                        case LEADERSHIP_TRANSFERRED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.TRANSFER_SUCCESS,
                                        Placeholder.unparsed("target", targetName)));
                        case ERROR_ALREADY_HANDLED -> {}
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
