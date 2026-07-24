package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.result.FriendResult;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command for removing friends.
 */
public final class FriendRemoveCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendRemoveCommand.class);

    private static final SuggestionProvider<CommandSource> FRIEND_SUGGESTIONS = (context, builder) -> {
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService) {
        return BrigadierCommand
                .literalArgumentBuilder("remove")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_REMOVE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("friend_name", StringArgumentType.word())
                        .suggests(FRIEND_SUGGESTIONS)
                        .executes(context -> handleRemove(context, friendService))
                );
    }

    private static int handleRemove(@NonNull CommandContext<CommandSource> context, FriendService friendService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "friend_name");

        friendService.resolveTargetPlayer(targetName)
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

                    PlayerRecord targetRecord = targetOptional.get();
                    UUID targetUuid = targetRecord.playerUuid();
                    String targetUsername = targetRecord.username();
                    friendService.removeFriend(sender.getUniqueId(), targetUuid)
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to remove friend {} from {}", targetUuid, sender.getUniqueId(), throwable);
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return FriendResult.ERROR_ALREADY_HANDLED;
                            })
                            .thenAccept(result -> {
                                switch (result) {
                                    case PLAYER_NOT_FOUND ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                                    case NOT_FRIENDS ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_NOT_FRIENDS, Placeholder.unparsed("target", targetUsername)));
                                    case FRIEND_REMOVED ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REMOVE_SUCCESS, Placeholder.unparsed("target", targetUsername)));
                                    case ERROR_ALREADY_HANDLED -> {}
                                    default ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                }
                            });
                });

        return Command.SINGLE_SUCCESS;
    }

}
