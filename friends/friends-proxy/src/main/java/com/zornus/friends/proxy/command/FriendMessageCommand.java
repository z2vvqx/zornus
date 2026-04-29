package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command for sending messages to friends.
 */
public final class FriendMessageCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendMessageCommand.class);

    private static final SuggestionProvider<CommandSource> FRIEND_SUGGESTIONS = (context, builder) -> {
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("message")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_MESSAGE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("friend_name", StringArgumentType.word())
                        .suggests(FRIEND_SUGGESTIONS)
                        .executes(context -> {
                            context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_MESSAGE));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("message_array", StringArgumentType.greedyString())
                                .executes(context -> {
                                    CommandSource source = context.getSource();
                                    if (!(source instanceof Player sender)) {
                                        source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String targetName = StringArgumentType.getString(context, "friend_name");
                                    String message = StringArgumentType.getString(context, "message_array");
                                    return handleFriendMessage(sender, targetName, message, friendService, proxyServer);
                                })
                        )
                );
    }

    private static int handleFriendMessage(Player sender, String targetName, String message,
                                           FriendService friendService, ProxyServer proxyServer) {
        resolveTargetPlayer(targetName, proxyServer, friendService)
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
                    handleMessageSend(sender, targetUuid, targetName, message, friendService);
                });

        return Command.SINGLE_SUCCESS;
    }

    public static void handleMessageSend(@NonNull Player sender, UUID targetUuid, String targetName, String message, @NonNull FriendService friendService) {
        friendService.sendFriendMessage(sender.getUniqueId(), targetUuid, message)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send friend message from {} to {}", sender.getUniqueId(), targetUuid, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return FriendResult.SUCCESS;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case PLAYER_NOT_FOUND ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        case MESSAGE_TOO_LONG ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_MESSAGE_TOO_LONG, Placeholder.unparsed("max_length", String.valueOf(FriendProxyConstants.MAX_MESSAGE_LENGTH))));
                        case NOT_FRIENDS ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_NOT_FRIENDS, Placeholder.unparsed("target", targetName)));
                        case FRIEND_NOT_ONLINE ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_FRIEND_OFFLINE, Placeholder.unparsed("target", targetName)));
                        case PLAYER_NOT_ACCEPTING_MESSAGES ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_PLAYER_NOT_ACCEPTING_MESSAGES, Placeholder.unparsed("target", targetName)));
                        case MESSAGE_SENT ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.MESSAGE_SENT_FORMAT,
                                        TagResolver.resolver(
                                                Placeholder.unparsed("target", targetName),
                                                Placeholder.unparsed("message", message)
                                        )));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });
    }

    private static CompletableFuture<Optional<UUID>> resolveTargetPlayer(String username, @NonNull ProxyServer proxyServer, FriendService friendService) {
        Optional<Player> onlinePlayer = proxyServer.getPlayer(username);
        if (onlinePlayer.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(onlinePlayer.get().getUniqueId()));
        }
        return friendService.fetchPlayerByUsername(username).thenApply(optional -> optional.map(playerRecord -> playerRecord.playerUuid()));
    }
}
