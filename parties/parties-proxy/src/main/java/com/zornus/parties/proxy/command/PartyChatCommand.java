package com.zornus.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.PartyResult;
import com.zornus.parties.proxy.service.PartyService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for sending messages to party members.
 */
public final class PartyChatCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyChatCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("chat")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PartyProxyConstants.USAGE_CHAT));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(context -> handlePartyChat(context, partyService))
                );
    }

    private static int handlePartyChat(@NonNull CommandContext<CommandSource> context, PartyService partyService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String message = StringArgumentType.getString(context, "message");

        partyService.sendPartyChat(sender, message)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send party chat from player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case MESSAGE_TOO_LONG ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_MESSAGE_TOO_LONG,
                                        Placeholder.unparsed("max_length", String.valueOf(PartyProxyConstants.MAX_MESSAGE_LENGTH))));
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_IN_PARTY));
                        case CHAT_DISABLED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_CHAT_DISABLED));
                        case CHAT_SENT -> {
                            // Message already sent by the service, nothing more to do
                        }
                        case ERROR_ALREADY_HANDLED -> {}
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
