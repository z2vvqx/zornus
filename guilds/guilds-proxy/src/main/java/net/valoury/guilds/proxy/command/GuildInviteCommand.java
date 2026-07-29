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
                .thenAccept(result -> handleInvitationResult(sender, result))
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to invite player {} to guild", targetName, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleInvitationResult(@NonNull Player sender,
                                               GuildResults.SendInvitation result) {
        switch (result) {
            case GuildResults.SendInvitation.Sent sent ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.INVITE_SUCCESS,
                            Placeholder.unparsed("target", sent.targetName())));
            case GuildResults.SendInvitation.NotInGuild ignored ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.INVITE_ERROR_NOT_IN_GUILD));
            case GuildResults.SendInvitation.NotLeader ignored ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_LEADER));
            case GuildResults.SendInvitation.PlayerNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            case GuildResults.SendInvitation.CannotInviteSelf ignored ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.INVITE_ERROR_CANNOT_INVITE_SELF));
            case GuildResults.SendInvitation.TargetAlreadyInGuild targetAlreadyInGuild ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.INVITE_ERROR_TARGET_IN_GUILD,
                            Placeholder.unparsed("target", targetAlreadyInGuild.targetName())));
            case GuildResults.SendInvitation.TargetInAnotherGuild targetInAnotherGuild ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.INVITE_ERROR_TARGET_IN_ANOTHER_GUILD,
                            Placeholder.unparsed("target", targetInAnotherGuild.targetName())));
            case GuildResults.SendInvitation.GuildFull ignored -> sender.sendMessage(StringUtils.deserialize(
                    GuildProxyConstants.INVITE_ERROR_GUILD_FULL,
                    Placeholder.unparsed("maximum_size", String.valueOf(GuildProxyConstants.MAX_GUILD_SIZE))));
            case GuildResults.SendInvitation.AlreadyInvited alreadyInvited ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.INVITE_ERROR_ALREADY_SENT,
                            Placeholder.unparsed("target", alreadyInvited.targetName())));
            case GuildResults.SendInvitation.SenderLimitReached ignored ->
                    sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_SENDER_INVITATION_LIMIT_REACHED));
            case GuildResults.SendInvitation.ReceiverLimitReached receiverLimitReached ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.ERROR_RECEIVER_INVITATION_LIMIT_REACHED,
                            Placeholder.unparsed("target", receiverLimitReached.targetName())));
            case GuildResults.SendInvitation.CooldownActive cooldownActive ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.ERROR_INVITATION_COOLDOWN,
                            TagResolver.resolver(
                                    Placeholder.unparsed("target", cooldownActive.targetName()),
                                    Placeholder.unparsed("time_remaining", "a moment"))));
            case GuildResults.SendInvitation.InvitesDisabled invitesDisabled ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.SETTINGS_ERROR_INVITES_DISABLED,
                            Placeholder.unparsed("target", invitesDisabled.targetName())));
            case GuildResults.SendInvitation.InvitesFriendsOnly invitesFriendsOnly ->
                    sender.sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.SETTINGS_ERROR_INVITES_FRIENDS_ONLY,
                            Placeholder.unparsed("target", invitesFriendsOnly.targetName())));
            case GuildResults.SendInvitation.GuildNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
        }
    }
}
