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
import net.valoury.friends.proxy.model.result.SendFriendRequestResult;
import net.valoury.friends.proxy.service.FriendService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.SocialRequestActions;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command for sending friend requests.
 */
public final class FriendAddCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendAddCommand.class);

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
                .literalArgumentBuilder("add")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_ADD));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleSendRequest(context, friendService))
                );
    }

    private static int handleSendRequest(@NonNull CommandContext<CommandSource> context, FriendService friendService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "player_name");

        friendService.resolveTargetPlayer(sender, targetName)
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
                    friendService.sendFriendRequest(sender.getUniqueId(), targetUuid)
                            .thenAccept(result -> {
                                switch (result) {
                                    case SendFriendRequestResult.CannotAddSelf ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_CANNOT_PERFORM_ON_SELF));
                                    case SendFriendRequestResult.AlreadyFriends ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_ALREADY_FRIENDS, Placeholder.unparsed("target", targetUsername)));
                                    case SendFriendRequestResult.AlreadySent ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ERROR_ALREADY_SENT, Placeholder.unparsed("target", targetUsername)));
                                    case SendFriendRequestResult.SenderFriendLimitReached ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_SENDER_FRIENDS_LIMIT_REACHED));
                                    case SendFriendRequestResult.ReceiverFriendLimitReached ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_RECEIVER_FRIENDS_LIMIT_REACHED, Placeholder.unparsed("target", targetUsername)));
                                    case SendFriendRequestResult.SenderRequestLimitReached ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_SENDER_REQUEST_LIMIT_REACHED));
                                    case SendFriendRequestResult.ReceiverRequestLimitReached ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_RECEIVER_REQUEST_LIMIT_REACHED, Placeholder.unparsed("target", targetUsername)));
                                    case SendFriendRequestResult.CooldownActive ignored ->
                                            sender.sendMessage(StringUtils.deserialize(
                                                    FriendProxyConstants.ERROR_REQUEST_COOLDOWN,
                                                    TagResolver.resolver(
                                                            Placeholder.unparsed("target", targetUsername),
                                                            Placeholder.unparsed("time_remaining", "a moment"))));
                                    case SendFriendRequestResult.ReceiverNotAcceptingRequests ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_PLAYER_NOT_ACCEPTING_REQUESTS, Placeholder.unparsed("target", targetUsername)));
                                    case SendFriendRequestResult.Sent ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.REQUEST_ADD_SUCCESS, Placeholder.unparsed("target", targetUsername)));
                                    case SendFriendRequestResult.IncomingRequestExists ignored ->
                                            sender.sendMessage(StringUtils.deserialize(
                                                    FriendProxyConstants.REQUEST_INCOMING_EXISTS,
                                                    TagResolver.resolver(
                                                            Placeholder.unparsed("target", targetUsername),
                                                            Placeholder.component(
                                                                    "checkmark_action",
                                                                    SocialRequestActions.checkmarkAction(
                                                                            "/friend accept " + targetUsername
                                                                    )
                                                            ),
                                                            Placeholder.component(
                                                                    "crossmark_action",
                                                                    SocialRequestActions.crossmarkAction(
                                                                            "/friend reject " + targetUsername
                                                                    )
                                                            )
                                                    )
                                            ));
                                }
                            })
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to send friend request from {} to {}",
                                        sender.getUniqueId(), targetUuid, throwable);
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return null;
                            });
                });

        return Command.SINGLE_SUCCESS;
    }
}
