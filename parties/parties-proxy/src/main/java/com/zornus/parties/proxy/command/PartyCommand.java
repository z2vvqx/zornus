package com.zornus.parties.proxy.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.parties.proxy.service.PartyService;
import org.jspecify.annotations.NonNull;

/**
 * Main command for party functionality.
 */
public final class PartyCommand {

    public static @NonNull BrigadierCommand create(PartyService partyService, ProxyServer proxyServer) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand
                .literalArgumentBuilder("party")
                .requires(source -> source instanceof com.velocitypowered.api.proxy.Player)
                .executes(PartyHelpCommand.defaultExecutor(partyService))
                .then(PartyHelpCommand.create(partyService))
                .then(PartyCreateCommand.create(partyService))
                .then(PartyInviteCommand.create(partyService, proxyServer))
                .then(PartyAcceptCommand.create(partyService, proxyServer))
                .then(PartyRejectCommand.create(partyService, proxyServer))
                .then(PartyUninviteCommand.create(partyService, proxyServer))
                .then(PartyLeaveCommand.create(partyService))
                .then(PartyListCommand.create(partyService, proxyServer))
                .then(PartyRequestsCommand.create(partyService, proxyServer))
                .then(PartyChatCommand.create(partyService))
                .then(PartyKickCommand.create(partyService, proxyServer))
                .then(PartyTransferCommand.create(partyService, proxyServer))
                .then(PartyWarpCommand.create(partyService))
                .then(PartyDisbandCommand.create(partyService))
                .then(PartyJumpCommand.create(partyService))
                .then(PartySettingsCommand.create(partyService))
                .build();

        return new BrigadierCommand(node);
    }
}
