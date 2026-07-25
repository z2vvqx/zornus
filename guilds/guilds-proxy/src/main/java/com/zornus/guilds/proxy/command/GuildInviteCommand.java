package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.model.result.GuildResults;
import com.zornus.guilds.proxy.service.GuildService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class GuildInviteCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildInviteCommand.class);

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

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("invite")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_INVITE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleInvitePlayer(context, guildService))
                );
    }

    private static int handleInvitePlayer(@NonNull CommandContext<CommandSource> context,
                                          GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "player_name");
        guildService.sendInvitation(sender, targetName)
                .thenAccept(result -> handleInvitationResult(sender, targetName, result))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to invite player {} to guild", targetName, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleInvitationResult(@NonNull Player sender, @NonNull String targetName,
                                               GuildResults.SendInvitation result) {
        TagResolver targetResolver = Placeholder.unparsed("target", targetName);
        switch (result.legacy()) {
            case INVITATION_SENT ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.INVITE_SUCCESS, targetResolver));
            case NOT_IN_GUILD ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.INVITE_ERROR_NOT_IN_GUILD));
            case NOT_LEADER ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_LEADER));
            case PLAYER_NOT_FOUND ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            case CANNOT_INVITE_SELF ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.INVITE_ERROR_CANNOT_INVITE_SELF));
            case TARGET_ALREADY_IN_GUILD ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.INVITE_ERROR_TARGET_IN_GUILD, targetResolver));
            case TARGET_IN_ANOTHER_GUILD -> sender.sendMessage(StringUtils.deserialize(
                    GuildProxyConstants.INVITE_ERROR_TARGET_IN_ANOTHER_GUILD, targetResolver));
            case GUILD_FULL -> sender.sendMessage(StringUtils.deserialize(
                    GuildProxyConstants.INVITE_ERROR_GUILD_FULL,
                    Placeholder.unparsed("maximum_size", String.valueOf(GuildProxyConstants.MAX_GUILD_SIZE))));
            case ALREADY_INVITED -> sender.sendMessage(StringUtils.deserialize(
                    GuildProxyConstants.INVITE_ERROR_ALREADY_SENT, targetResolver));
            case SENDER_INVITATION_LIMIT_REACHED ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_SENDER_INVITATION_LIMIT_REACHED));
            case RECEIVER_INVITATION_LIMIT_REACHED -> sender.sendMessage(StringUtils.deserialize(
                    GuildProxyConstants.ERROR_RECEIVER_INVITATION_LIMIT_REACHED, targetResolver));
            case INVITATION_COOLDOWN_ACTIVE -> sender.sendMessage(StringUtils.deserialize(
                    GuildProxyConstants.ERROR_INVITATION_COOLDOWN,
                    TagResolver.resolver(
                            Placeholder.unparsed("target", targetName),
                            Placeholder.unparsed("time_remaining", "a moment"))));
            case INVITES_DISABLED -> sender.sendMessage(StringUtils.deserialize(
                    GuildProxyConstants.SETTINGS_ERROR_INVITES_DISABLED, targetResolver));
            case INVITES_FRIENDS_ONLY -> sender.sendMessage(StringUtils.deserialize(
                    GuildProxyConstants.SETTINGS_ERROR_INVITES_FRIENDS_ONLY, targetResolver));
            default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
        }
    }
}
