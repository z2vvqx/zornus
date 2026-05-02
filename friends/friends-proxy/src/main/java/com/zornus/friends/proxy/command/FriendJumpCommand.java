package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.result.FriendResult;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command for teleporting to friends.
 */
public final class FriendJumpCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendJumpCommand.class);

    private static final SuggestionProvider<CommandSource> FRIEND_SUGGESTIONS = (context, builder) -> {
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("jump")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_JUMP));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("friend_name", StringArgumentType.word())
                        .suggests(FRIEND_SUGGESTIONS)
                        .executes(context -> handleFriendJump(context, friendService, proxyServer))
                );
    }

    private static int handleFriendJump(@NonNull CommandContext<CommandSource> context, FriendService friendService,
                                        ProxyServer proxyServer) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "friend_name");

        FriendCommandUtils.resolveTargetPlayer(targetName, proxyServer, friendService)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to resolve player by username: {}", targetName, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return Optional.empty();
                })
                .thenAccept(targetOptional -> {
                    if (targetOptional.isEmpty()) {
                        sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        return;
                    }

                    UUID targetUuid = targetOptional.get();
                    friendService.jumpToFriend(sender.getUniqueId(), targetUuid)
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to jump to friend {} from {}", targetUuid, sender.getUniqueId(), throwable);
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return FriendResult.ERROR_ALREADY_HANDLED;
                            })
                            .thenAccept(result -> {
                                switch (result) {
                                    case PLAYER_NOT_FOUND ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                                    case NOT_FRIENDS ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_NOT_FRIENDS, Placeholder.unparsed("target", targetName)));
                                    case FRIEND_NOT_ONLINE ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_FRIEND_OFFLINE, Placeholder.unparsed("target", targetName)));
                                    case PLAYER_NOT_ALLOWING_JUMP ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_PLAYER_NOT_ALLOWING_JUMP, Placeholder.unparsed("target", targetName)));
                                    case FRIEND_NO_INSTANCE ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.JUMP_ERROR_NO_INSTANCE, Placeholder.unparsed("target", targetName)));
                                    case ALREADY_IN_SAME_INSTANCE ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.JUMP_INFO_SAME_INSTANCE, Placeholder.unparsed("target", targetName)));
                                    case JUMP_SUCCESSFUL ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.JUMP_SUCCESS, Placeholder.unparsed("target", targetName)));
                                    case ERROR_ALREADY_HANDLED -> {}
                                    default ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                }
                            });
                });

        return Command.SINGLE_SUCCESS;
    }

}
