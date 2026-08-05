package net.valoury.parties.proxy.command;

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
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.result.PartyResults;
import net.valoury.parties.proxy.service.PartyService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Command for inviting players to party.
 */
public final class PartyInviteCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyInviteCommand.class);

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

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("invite")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PartyProxyConstants.USAGE_INVITE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleInvitePlayer(context, partyService))
                );
    }

    private static int handleInvitePlayer(@NonNull CommandContext<CommandSource> context,
                                          PartyService partyService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "player_name");
        partyService.sendInvitation(sender, targetName)
                .thenAccept(result -> {
                    switch (result) {
                        case PartyResults.SendInvitation.NotLeader ignored ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_LEADER));
                        case PartyResults.SendInvitation.InsufficientRole ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.ERROR_INSUFFICIENT_ROLE));
                        case PartyResults.SendInvitation.PlayerNotFound ignored ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        case PartyResults.SendInvitation.CannotInviteSelf ignored ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_ERROR_CANNOT_INVITE_SELF));
                        case PartyResults.SendInvitation.TargetAlreadyInParty targetAlreadyInParty ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_ERROR_TARGET_IN_PARTY,
                                        Placeholder.unparsed("target", targetAlreadyInParty.targetName())));
                        case PartyResults.SendInvitation.PartyFull ignored ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_ERROR_PARTY_FULL,
                                        Placeholder.unparsed("maximum_size", String.valueOf(PartyProxyConstants.MAX_PARTY_SIZE))));
                        case PartyResults.SendInvitation.CooldownActive cooldownActive ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.ERROR_INVITATION_COOLDOWN,
                                        TagResolver.resolver(
                                                Placeholder.unparsed("target", cooldownActive.targetName()),
                                                Placeholder.unparsed("time_remaining", "a moment"))));
                        case PartyResults.SendInvitation.SenderLimitReached ignored ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_SENDER_INVITATION_LIMIT_REACHED));
                        case PartyResults.SendInvitation.ReceiverLimitReached receiverLimitReached ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_RECEIVER_INVITATION_LIMIT_REACHED,
                                        Placeholder.unparsed("target", receiverLimitReached.targetName())));
                        case PartyResults.SendInvitation.InvitesDisabled invitesDisabled ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.SETTINGS_ERROR_INVITES_DISABLED,
                                        Placeholder.unparsed("target", invitesDisabled.targetName())));
                        case PartyResults.SendInvitation.InvitesFriendsOnly invitesFriendsOnly ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.SETTINGS_ERROR_INVITES_FRIENDS_ONLY,
                                        Placeholder.unparsed("target", invitesFriendsOnly.targetName())));
                        case PartyResults.SendInvitation.AlreadyInvited alreadyInvited ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_ERROR_ALREADY_SENT,
                                        Placeholder.unparsed("target", alreadyInvited.targetName())));
                        case PartyResults.SendInvitation.Sent sent ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_SUCCESS,
                                        Placeholder.unparsed("target", sent.targetName())));
                        case PartyResults.SendInvitation.PartyNotFound ignored ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send party invitation from {} to {}",
                            sender.getUniqueId(), targetName, throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
