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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.result.GuildRankChangeResult;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class GuildDemoteCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildDemoteCommand.class);

    private static SuggestionProvider<CommandSource> onlinePlayerSuggestions(
            ProxyServer proxyServer
    ) {
        return (context, builder) -> {
            String remainingInput = builder.getRemainingLowerCase();
            if (remainingInput.isEmpty()) {
                return builder.buildFuture();
            }
            proxyServer.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(username -> username.toLowerCase(Locale.ROOT)
                            .startsWith(remainingInput))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    public static LiteralArgumentBuilder<CommandSource> create(
            GuildService guildService,
            ProxyServer proxyServer
    ) {
        return BrigadierCommand
                .literalArgumentBuilder("demote")
                .executes(context -> {
                    context.getSource().sendMessage(
                            StringUtils.deserialize(GuildProxyConstants.USAGE_DEMOTE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder(
                                "member_name",
                                StringArgumentType.word()
                        )
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleDemoteMember(context, guildService))
                );
    }

    private static int handleDemoteMember(
            @NonNull CommandContext<CommandSource> context,
            GuildService guildService
    ) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "member_name");
        guildService.demoteMember(sender, targetName)
                .thenAccept(result -> handleResult(sender, result))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to demote guild member {}", targetName, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }

    private static void handleResult(
            @NonNull Player sender,
            @NonNull GuildRankChangeResult result
    ) {
        switch (result) {
            case GuildRankChangeResult.Changed ignored -> {
                // The guild-wide demotion announcement is also the actor's confirmation.
            }
            case GuildRankChangeResult.NotInGuild ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.ERROR_NOT_IN_GUILD));
            case GuildRankChangeResult.PlayerNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            SharedConstants.PLAYER_NOT_FOUND));
            case GuildRankChangeResult.PlayerNotInGuild playerNotInGuild ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.KICK_ERROR_PLAYER_NOT_IN_GUILD,
                            Placeholder.unparsed("target", playerNotInGuild.targetName())
                    ));
            case GuildRankChangeResult.CannotChangeOwnRank ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.RANK_ERROR_CANNOT_CHANGE_SELF));
            case GuildRankChangeResult.InsufficientRank ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.ERROR_INSUFFICIENT_RANK));
            case GuildRankChangeResult.CannotManageRank ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.RANK_ERROR_CANNOT_MANAGE));
            case GuildRankChangeResult.PromotionWouldMatchActorRank ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            SharedConstants.ERROR_UNEXPECTED));
            case GuildRankChangeResult.AlreadyHighestRank ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            SharedConstants.ERROR_UNEXPECTED));
            case GuildRankChangeResult.AlreadyLowestRank alreadyLowestRank ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.RANK_ERROR_ALREADY_LOWEST,
                            Placeholder.unparsed("target", alreadyLowestRank.targetName())
                    ));
            case GuildRankChangeResult.GuildNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            SharedConstants.ERROR_UNEXPECTED));
        }
    }
}
