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
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command for sending friend requests.
 */
public final class FriendAddCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendAddCommand.class);

    private static final SuggestionProvider<CommandSource> PLAYER_SUGGESTIONS = (context, builder) -> {
        // Suggest online players (would need ProxyServer access for real implementation)
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("add")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_ADD));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(context -> handleFriendAdd(context, friendService, proxyServer))
                );
    }

    private static int handleFriendAdd(@NonNull CommandContext<CommandSource> context, FriendService friendService,
                                       ProxyServer proxyServer) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "player_name");

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
                    friendService.sendFriendRequest(sender.getUniqueId(), targetUuid)
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to send friend request from {} to {}", sender.getUniqueId(), targetUuid, throwable);
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return FriendResult.SUCCESS;
                            })
                            .thenAccept(result -> {
                                switch (result) {
                                    case PLAYER_NOT_FOUND ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                                    case CANNOT_ADD_SELF ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_CANNOT_PERFORM_ON_SELF));
                                    case ALREADY_FRIENDS ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_ALREADY_FRIENDS, Placeholder.unparsed("target", targetName)));
                                    case REQUEST_ALREADY_SENT ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ERROR_ALREADY_SENT, Placeholder.unparsed("target", targetName)));
                                    case SENDER_FRIENDS_LIMIT_REACHED ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_SENDER_FRIENDS_LIMIT_REACHED));
                                    case RECEIVER_FRIENDS_LIMIT_REACHED ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_RECEIVER_FRIENDS_LIMIT_REACHED, Placeholder.unparsed("target", targetName)));
                                    case SENDER_REQUEST_LIMIT_REACHED ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_SENDER_REQUEST_LIMIT_REACHED));
                                    case RECEIVER_REQUEST_LIMIT_REACHED ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_RECEIVER_REQUEST_LIMIT_REACHED, Placeholder.unparsed("target", targetName)));
                                    case REQUEST_COOLDOWN_ACTIVE ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_REQUEST_COOLDOWN,
                                                    TagResolver.resolver(
                                                            Placeholder.unparsed("target", targetName),
                                                            Placeholder.unparsed("time_remaining", "1 minute")
                                                    )));
                                    case PLAYER_NOT_ACCEPTING_REQUESTS ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_PLAYER_NOT_ACCEPTING_REQUESTS, Placeholder.unparsed("target", targetName)));
                                    case REQUEST_SENT ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ADD_SUCCESS, Placeholder.unparsed("target", targetName)));
                                    case REQUEST_ACCEPTED_AUTOMATICALLY ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ACCEPT_SUCCESS_AUTO, Placeholder.unparsed("target", targetName)));
                                    default ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                }
                            });
                });

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Optional<UUID>> resolveTargetPlayer(String username, @NonNull ProxyServer proxyServer, FriendService friendService) {
        Optional<Player> onlinePlayer = proxyServer.getPlayer(username);
        if (onlinePlayer.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(onlinePlayer.get().getUniqueId()));
        }
        return friendService.fetchPlayerByUsername(username).thenApply(optional -> optional.map(playerRecord -> playerRecord.playerUuid()));
    }
}
