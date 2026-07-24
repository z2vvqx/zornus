package com.zornus.friends.proxy.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.friends.proxy.service.FriendService;
import org.jspecify.annotations.NonNull;

/**
 * Main command for friend functionality.
 */
public final class FriendCommand {

    public static @NonNull BrigadierCommand create(FriendService friendService, ProxyServer proxyServer) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand
                .literalArgumentBuilder("friend")
                .requires(source -> source instanceof Player)
                .executes(FriendHelpCommand.defaultExecutor(friendService))
                .then(FriendHelpCommand.create(friendService))
                .then(FriendAddCommand.create(friendService))
                .then(FriendRevokeCommand.create(friendService))
                .then(FriendAcceptCommand.create(friendService))
                .then(FriendRejectCommand.create(friendService))
                .then(FriendRemoveCommand.create(friendService))
                .then(FriendMessageCommand.create(friendService))
                .then(FriendReplyCommand.create(friendService))
                .then(FriendJumpCommand.create(friendService))
                .then(FriendListCommand.create(friendService, proxyServer))
                .then(FriendRequestsCommand.create(friendService))
                .then(FriendPresenceCommand.create(friendService))
                .then(FriendSettingsCommand.create(friendService))
                .build();

        return new BrigadierCommand(node);
    }
}
