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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.friends.proxy.FriendProxyConstants;
import net.valoury.friends.proxy.model.result.JumpToFriendResult;
import net.valoury.friends.proxy.service.FriendService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Command for teleporting to friends.
 */
public final class FriendJumpCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendJumpCommand.class);

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
                .literalArgumentBuilder("jump")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(FriendProxyConstants.USAGE_JUMP));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("friend_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleJumpToFriend(context, friendService))
                );
    }

    private static int handleJumpToFriend(@NonNull CommandContext<CommandSource> context, FriendService friendService) {
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
                    friendService.jumpToFriend(sender.getUniqueId(), targetUuid)
                            .thenAccept(result -> {
                                switch (result) {
                                    case JumpToFriendResult.NotFriends ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_NOT_FRIENDS, Placeholder.unparsed("target", targetUsername)));
                                    case JumpToFriendResult.FriendNotOnline ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_FRIEND_OFFLINE, Placeholder.unparsed("target", targetUsername)));
                                    case JumpToFriendResult.TargetNotAllowingJump ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_PLAYER_NOT_ALLOWING_JUMP, Placeholder.unparsed("target", targetUsername)));
                                    case JumpToFriendResult.FriendHasNoInstance ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.JUMP_ERROR_NO_INSTANCE, Placeholder.unparsed("target", targetUsername)));
                                    case JumpToFriendResult.AlreadyInSameInstance ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.JUMP_INFO_SAME_INSTANCE, Placeholder.unparsed("target", targetUsername)));
                                    case JumpToFriendResult.Jumped ignored ->
                                            sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.JUMP_SUCCESS, Placeholder.unparsed("target", targetUsername)));
                                    case JumpToFriendResult.JumpFailed ignored ->
                                            sender.sendMessage(StringUtils.deserialize(
                                                    FriendProxyConstants.JUMP_ERROR_FAILED,
                                                    Placeholder.unparsed("target", targetUsername)));
                                    case JumpToFriendResult.PlayerNotOnline ignored ->
                                            sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                }
                            })
                            .exceptionally(throwable -> {
                                LOGGER.error("Failed to jump to friend {} from {}",
                                        targetUuid, sender.getUniqueId(), throwable);
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                                return null;
                            });
                });

        return Command.SINGLE_SUCCESS;
    }

}
