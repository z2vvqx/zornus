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
 * Command for warping party members to the leader.
 */
public final class PartyWarpCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyWarpCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("warp")
                .executes(context -> handleWarpParty(context, partyService));
    }

    private static int handleWarpParty(@NonNull CommandContext<CommandSource> context, PartyService partyService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        partyService.warpParty(sender)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to warp party for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.SUCCESS;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.WARP_ERROR_NOT_IN_PARTY));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_LEADER));
                        case WARP_ON_COOLDOWN ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.WARP_ERROR_ON_COOLDOWN));
                        case LEADER_NO_INSTANCE ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.WARP_ERROR_NO_INSTANCE));
                        case PARTY_WARPED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.WARP_SUCCESS));
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
