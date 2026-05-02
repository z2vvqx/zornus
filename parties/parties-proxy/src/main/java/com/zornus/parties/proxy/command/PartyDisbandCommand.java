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
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for disbanding party.
 */
public final class PartyDisbandCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyDisbandCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("disband")
                .executes(context -> handleDisbandParty(context, partyService, false))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("confirmation", StringArgumentType.word())
                        .executes(context -> {
                            String confirmation = StringArgumentType.getString(context, "confirmation");
                            return handleDisbandParty(context, partyService, "confirm".equalsIgnoreCase(confirmation));
                        })
                );
    }

    private static int handleDisbandParty(@NonNull CommandContext<CommandSource> context, PartyService partyService, boolean isConfirming) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        partyService.disbandParty(sender, isConfirming)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to disband party for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.SUCCESS;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.DISBAND_ERROR_NOT_IN_PARTY));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_LEADER));
                        case DISBAND_CONFIRMATION_REQUIRED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.DISBAND_CONFIRMATION_REQUIRED));
                        case NO_CONFIRMATION_PENDING ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.DISBAND_ERROR_NO_CONFIRMATION));
                        case PARTY_DISBANDED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.DISBAND_SUCCESS));
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
