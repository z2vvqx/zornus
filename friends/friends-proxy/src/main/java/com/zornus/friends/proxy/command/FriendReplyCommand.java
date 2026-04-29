package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Command for replying to friend messages.
 */
public final class FriendReplyCommand {

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService) {
        return BrigadierCommand
                .literalArgumentBuilder("reply")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_REPLY));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("message_array", StringArgumentType.greedyString())
                        .executes(context -> handleReplyFriend(context, friendService))
                );
    }

    private static int handleReplyFriend(@NonNull CommandContext<CommandSource> context, FriendService friendService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String message = StringArgumentType.getString(context, "message_array");

        friendService.fetchLastMessageSender(sender.getUniqueId()).thenAccept(lastSenderOptional -> {
            if (lastSenderOptional.isEmpty()) {
                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.MESSAGE_ERROR_NO_REPLY_TARGET));
                return;
            }

            UUID targetUuid = lastSenderOptional.get();
            friendService.fetchPlayerByUuid(targetUuid).thenAccept(playerOptional -> {
                String targetName = playerOptional.map(player -> player.username()).orElse("Unknown");
                FriendMessageCommand.handleMessageSend(sender, targetUuid, targetName, message, friendService);
            });
        });

        return Command.SINGLE_SUCCESS;
    }
}
