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
 * Command for revoking friend requests.
 */
public final class FriendRevokeCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendRevokeCommand.class);

    private static final SuggestionProvider<CommandSource> OUTGOING_REQUEST_SUGGESTIONS = (context, builder) -> {
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("revoke")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_REVOKE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(OUTGOING_REQUEST_SUGGESTIONS)
                        .executes(context -> handleRevokeFriend(context, friendService, proxyServer))
                );
    }

    private static int handleRevokeFriend(@NonNull CommandContext<CommandSource> context, FriendService friendService,
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
                    friendService.revokeFriendRequest(sender.getUniqueId(), targetUuid)
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to revoke friend request from {} to {}", sender.getUniqueId(), targetUuid, throwable);
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return FriendResult.SUCCESS;
                            })
                            .thenAccept(result -> {
                                switch (result) {
                                    case PLAYER_NOT_FOUND ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                                    case NO_REQUEST_FOUND ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ERROR_NOT_FOUND, Placeholder.unparsed("target", targetName)));
                                    case REQUEST_REVOKED ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_REVOKE_SUCCESS, Placeholder.unparsed("target", targetName)));
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
