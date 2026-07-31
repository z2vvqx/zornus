package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.result.GuildResults;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class GuildKickCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildKickCommand.class);

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
                .literalArgumentBuilder("kick")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_KICK));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("member_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleKickMember(context, guildService))
                );
    }

    private static int handleKickMember(@NonNull CommandContext<CommandSource> context,
                                        GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "member_name");
        guildService.kickMember(sender, targetName)
                .thenAccept(result -> handleKickResult(sender, result))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to kick guild member {}", targetName, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleKickResult(
            @NonNull Player sender,
            GuildResults.KickMember result
    ) {
        switch (result) {
            case GuildResults.KickMember.Removed removed ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.KICK_SUCCESS,
                            Placeholder.unparsed("target", removed.targetName())));
            case GuildResults.KickMember.NotInGuild ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.KICK_ERROR_NOT_IN_GUILD));
            case GuildResults.KickMember.InsufficientRank ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.ERROR_INSUFFICIENT_RANK));
            case GuildResults.KickMember.PlayerNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            case GuildResults.KickMember.PlayerNotInGuild playerNotInGuild ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.KICK_ERROR_PLAYER_NOT_IN_GUILD,
                            Placeholder.unparsed("target", playerNotInGuild.targetName())));
            case GuildResults.KickMember.CannotRemoveLeader ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.KICK_ERROR_CANNOT_KICK_LEADER));
            case GuildResults.KickMember.CannotRemoveSelf ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.KICK_ERROR_CANNOT_KICK_SELF));
            case GuildResults.KickMember.GuildDisbanded ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
            case GuildResults.KickMember.GuildNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
        }
    }
}
