package com.zornus.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.PartyResult;
import com.zornus.parties.proxy.service.PartyService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Command for inviting players to party.
 */
public final class PartyInviteCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyInviteCommand.class);

    private static final SuggestionProvider<CommandSource> PLAYER_SUGGESTIONS = (context, builder) -> {
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("invite")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PartyProxyConstants.USAGE_INVITE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(context -> handleInvitePlayer(context, partyService, proxyServer))
                );
    }

    private static int handleInvitePlayer(@NonNull CommandContext<CommandSource> context, PartyService partyService,
                                          ProxyServer proxyServer) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "player_name");

        Optional<Player> targetOptional = proxyServer.getPlayer(targetName);
        if (targetOptional.isEmpty()) {
            sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            return Command.SINGLE_SUCCESS;
        }
        Player target = targetOptional.get();

        partyService.sendInvitation(sender, target)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send party invitation from {} to {}", sender.getUniqueId(), target.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_ERROR_NOT_IN_PARTY));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_LEADER));
                        case CANNOT_INVITE_SELF ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_ERROR_CANNOT_INVITE_SELF));
                        case TARGET_ALREADY_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_ERROR_TARGET_IN_PARTY,
                                        Placeholder.unparsed("target", targetName)));
                        case PARTY_FULL ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_ERROR_PARTY_FULL,
                                        Placeholder.unparsed("maximum_size", String.valueOf(PartyProxyConstants.MAX_PARTY_SIZE))));
                        case INVITATION_COOLDOWN_ACTIVE ->
                                handleCooldownMessage(sender, target, targetName, partyService);
                        case INVITES_DISABLED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.SETTINGS_ERROR_INVITES_DISABLED,
                                        Placeholder.unparsed("target", targetName)));
                        case INVITES_FRIENDS_ONLY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.SETTINGS_ERROR_INVITES_FRIENDS_ONLY,
                                        Placeholder.unparsed("target", targetName)));
                        case ALREADY_INVITED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_ERROR_ALREADY_SENT,
                                        Placeholder.unparsed("target", targetName)));
                        case INVITATION_SENT ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.INVITE_SUCCESS,
                                        Placeholder.unparsed("target", targetName)));
                        case ERROR_ALREADY_HANDLED -> {}
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleCooldownMessage(Player sender, Player target, String targetName, PartyService partyService) {
        partyService.getRemainingInvitationCooldown(sender.getUniqueId(), target.getUniqueId())
                .thenAccept(remainingTime -> {
                    Component timeComponent = StringUtils.formatDuration(remainingTime);
                    TagResolver combinedResolver = TagResolver.resolver(
                            Placeholder.unparsed("target", targetName),
                            Placeholder.component("time_remaining", timeComponent)
                    );
                    sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_INVITATION_COOLDOWN, combinedResolver));
                });
    }
}
