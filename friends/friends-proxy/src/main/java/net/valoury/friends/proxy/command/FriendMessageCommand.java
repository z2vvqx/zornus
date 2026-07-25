package net.valoury.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.friends.proxy.FriendProxyConstants;
import net.valoury.friends.proxy.model.result.SendFriendMessageResult;
import net.valoury.friends.proxy.service.FriendService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
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
 * Command for sending messages to friends.
 */
public final class FriendMessageCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendMessageCommand.class);

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
                .literalArgumentBuilder("message")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_MESSAGE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("friend_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
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
                                    return handleSendMessage(sender, targetName, message, friendService);
                                })
                        )
                );
    }

    private static int handleSendMessage(Player sender, String targetName, String message,
                                         FriendService friendService) {
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
                    processMessageSend(sender, targetRecord.playerUuid(), targetRecord.username(), message, friendService);
                });

        return Command.SINGLE_SUCCESS;
    }

    public static void processMessageSend(@NonNull Player sender, UUID targetUuid, String targetName, String message, @NonNull FriendService friendService) {
        friendService.sendFriendMessage(sender.getUniqueId(), targetUuid, message)
                .thenAccept(result -> {
                    switch (result) {
                        case SendFriendMessageResult.MessageTooLong ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_MESSAGE_TOO_LONG, Placeholder.unparsed("max_length", String.valueOf(FriendProxyConstants.MAX_MESSAGE_LENGTH))));
                        case SendFriendMessageResult.NotFriends ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_NOT_FRIENDS, Placeholder.unparsed("target", targetName)));
                        case SendFriendMessageResult.FriendNotOnline ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_FRIEND_OFFLINE, Placeholder.unparsed("target", targetName)));
                        case SendFriendMessageResult.ReceiverNotAcceptingMessages ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_PLAYER_NOT_ACCEPTING_MESSAGES, Placeholder.unparsed("target", targetName)));
                        case SendFriendMessageResult.Sent ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.MESSAGE_SENT_FORMAT,
                                        TagResolver.resolver(
                                                Placeholder.unparsed("target", targetName),
                                                Placeholder.unparsed("message", message)
                                        )));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send friend message from {} to {}",
                            sender.getUniqueId(), targetUuid, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
    }

}
