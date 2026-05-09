package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.result.FriendReplyResult;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for replying to friend messages.
 */
public final class FriendReplyCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendReplyCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService) {
        return BrigadierCommand
                .literalArgumentBuilder("reply")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_REPLY));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("message_array", StringArgumentType.greedyString())
                        .executes(context -> handleReply(context, friendService))
                );
    }

    private static int handleReply(@NonNull CommandContext<CommandSource> context, FriendService friendService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String message = StringArgumentType.getString(context, "message_array");

        friendService.sendFriendReply(sender.getUniqueId(), message)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send friend reply from {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return new FriendReplyResult.ErrorAlreadyHandled();
                })
                .thenAccept(result -> {
                    switch (result) {
                        case FriendReplyResult.MessageTooLong ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_MESSAGE_TOO_LONG,
                                        Placeholder.unparsed("max_length", String.valueOf(FriendProxyConstants.MAX_MESSAGE_LENGTH))));
                        case FriendReplyResult.NoRecentMessage ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.MESSAGE_ERROR_NO_REPLY_TARGET));
                        case FriendReplyResult.NotFriends notFriends ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_NO_LONGER_FRIENDS_REPLY,
                                        Placeholder.unparsed("target", notFriends.targetName())));
                        case FriendReplyResult.FriendNotOnline friendNotOnline ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_FRIEND_OFFLINE,
                                        Placeholder.unparsed("target", friendNotOnline.targetName())));
                        case FriendReplyResult.PlayerNotAcceptingMessages playerNotAcceptingMessages ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_PLAYER_NOT_ACCEPTING_MESSAGES,
                                        Placeholder.unparsed("target", playerNotAcceptingMessages.targetName())));
                        case FriendReplyResult.Success ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.MESSAGE_REPLY_SUCCESS));
                        case FriendReplyResult.ErrorAlreadyHandled ignored -> {}
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
