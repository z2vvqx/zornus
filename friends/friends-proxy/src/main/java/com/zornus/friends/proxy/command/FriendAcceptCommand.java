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

import static com.zornus.friends.proxy.command.FriendCommandUtils.resolveTargetPlayer;

/**
 * Command for accepting friend requests.
 */
public final class FriendAcceptCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendAcceptCommand.class);

    private static final SuggestionProvider<CommandSource> PLAYER_SUGGESTIONS = (context, builder) -> {
        // Suggest players with pending requests
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("accept")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_ACCEPT));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(context -> handleFriendAccept(context, friendService, proxyServer))
                );
    }

    private static int handleFriendAccept(@NonNull CommandContext<CommandSource> context, FriendService friendService,
                                          ProxyServer proxyServer) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "player_name");

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
                    friendService.acceptFriendRequest(sender.getUniqueId(), targetUuid)
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to accept friend request from {} to {}", sender.getUniqueId(), targetUuid, throwable);
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return FriendResult.ERROR_ALREADY_HANDLED;
                            })
                            .thenAccept(result -> {
                                switch (result) {
                                    case PLAYER_NOT_FOUND ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                                    case NO_REQUEST_FOUND ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ERROR_NOT_FOUND, Placeholder.unparsed("target", targetName)));
                                    case REQUEST_ACCEPTED ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ACCEPT_SUCCESS, Placeholder.unparsed("target", targetName)));
                                    case ERROR_ALREADY_HANDLED -> {}
                                    default ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                }
                            });
                });

        return Command.SINGLE_SUCCESS;
    }

}
