package com.zornus.parties.proxy.command;

import com.mojang.brigadier.Command;
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
 * Command for creating new party.
 */
public final class PartyCreateCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyCreateCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("create")
                .executes(context -> handleCreateParty(context, partyService));
    }

    private static int handleCreateParty(@NonNull CommandContext<CommandSource> context, PartyService partyService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        partyService.createParty(sender)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to create party for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.SUCCESS;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case ALREADY_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_ALREADY_IN_PARTY));
                        case PARTY_CREATED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.CREATE_SUCCESS));
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
