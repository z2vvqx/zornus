package net.valoury.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.friends.proxy.FriendProxyConstants;
import net.valoury.friends.proxy.model.result.AcceptFriendRequestResult;
import net.valoury.friends.proxy.service.FriendService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command for accepting friend requests.
 */
public final class FriendAcceptCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendAcceptCommand.class);

    private static SuggestionProvider<CommandSource> onlinePlayerSuggestions(ProxyServer proxyServer) {
        return (context, builder) -> {
            String remainingInput = builder.getRemainingLowerCase();
            if (remainingInput.isEmpty()) {
                return builder.buildFuture();
            }
            proxyServer.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(username -> username.toLowerCase(Locale.ROOT).startsWith(remainingInput))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("accept")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_ACCEPT));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleAcceptRequest(context, friendService))
                );
    }

    private static int handleAcceptRequest(@NonNull CommandContext<CommandSource> context, FriendService friendService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "player_name");

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
                    friendService.acceptFriendRequest(sender.getUniqueId(), targetUuid)
                            .thenAccept(result -> {
                                switch (result) {
                                    case AcceptFriendRequestResult.NoRequestFound ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ERROR_NOT_FOUND, Placeholder.unparsed("target", targetUsername)));
                                    case AcceptFriendRequestResult.Accepted ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ACCEPT_SUCCESS, Placeholder.unparsed("target", targetUsername)));
                                    case AcceptFriendRequestResult.AccepterFriendLimitReached ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_SENDER_FRIENDS_LIMIT_REACHED));
                                    case AcceptFriendRequestResult.RequesterFriendLimitReached ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_RECEIVER_FRIENDS_LIMIT_REACHED,
                                                    Placeholder.unparsed("target", targetUsername)));
                                    case AcceptFriendRequestResult.AlreadyFriends ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_ALREADY_FRIENDS,
                                                    Placeholder.unparsed("target", targetUsername)));
                                }
                            })
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to accept friend request from {} to {}",
                                        sender.getUniqueId(), targetUuid, throwable);
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return null;
                            });
                });

        return Command.SINGLE_SUCCESS;
    }

}
