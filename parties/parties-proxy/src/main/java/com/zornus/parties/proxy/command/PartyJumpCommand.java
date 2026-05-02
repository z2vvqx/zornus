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
 * Command for jumping to the party leader.
 */
public final class PartyJumpCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyJumpCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("jump")
                .executes(context -> handleJumpToLeader(context, partyService));
    }

    private static int handleJumpToLeader(@NonNull CommandContext<CommandSource> context, PartyService partyService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        partyService.jumpToLeader(sender)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to jump to leader for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.SUCCESS;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.JUMP_ERROR_NOT_IN_PARTY));
                        case CANNOT_JUMP_AS_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.JUMP_ERROR_CANNOT_JUMP_AS_LEADER));
                        case LEADER_NOT_ONLINE ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.JUMP_ERROR_LEADER_NOT_ONLINE));
                        case LEADER_NO_INSTANCE ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.JUMP_ERROR_LEADER_NO_INSTANCE));
                        case ALREADY_WITH_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.JUMP_INFO_ALREADY_WITH_LEADER));
                        case JUMPED_TO_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.JUMP_SUCCESS));
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
