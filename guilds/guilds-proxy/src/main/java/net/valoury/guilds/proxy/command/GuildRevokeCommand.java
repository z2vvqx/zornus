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

public final class GuildRevokeCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildRevokeCommand.class);

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
                .literalArgumentBuilder("revoke")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_UNINVITE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleRevokeInvitation(context, guildService))
                );
    }

    private static int handleRevokeInvitation(@NonNull CommandContext<CommandSource> context,
                                              GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "player_name");
        guildService.revokeInvitation(sender, targetName)
                .thenAccept(result -> handleRevokeResult(sender, result))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to revoke guild invitation for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleRevokeResult(
            @NonNull Player sender,
            GuildResults.RevokeInvitation result
    ) {
        switch (result) {
            case GuildResults.RevokeInvitation.Revoked revoked ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.UNINVITE_SUCCESS,
                            Placeholder.unparsed("target", revoked.targetName())));
            case GuildResults.RevokeInvitation.NoInvitationFound noInvitationFound ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.UNINVITE_ERROR_NO_INVITATION,
                            Placeholder.unparsed("target", noInvitationFound.targetName())));
            case GuildResults.RevokeInvitation.NotInGuild ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.UNINVITE_ERROR_NOT_IN_GUILD));
            case GuildResults.RevokeInvitation.InsufficientRank ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.ERROR_INSUFFICIENT_RANK));
            case GuildResults.RevokeInvitation.PlayerNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            case GuildResults.RevokeInvitation.GuildNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
        }
    }
}
