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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Command for accepting party invitations.
 */
public final class PartyAcceptCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyAcceptCommand.class);

    private static final SuggestionProvider<CommandSource> PLAYER_SUGGESTIONS = (context, builder) -> {
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("accept")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PartyProxyConstants.USAGE_ACCEPT));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("leader_name", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(context -> handleAcceptInvitation(context, partyService, proxyServer))
                );
    }

    private static int handleAcceptInvitation(@NonNull CommandContext<CommandSource> context, PartyService partyService,
                                               ProxyServer proxyServer) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "leader_name");

        Optional<Player> targetOptional = proxyServer.getPlayer(targetName);
        if (targetOptional.isEmpty()) {
            sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            return Command.SINGLE_SUCCESS;
        }
        Player target = targetOptional.get();

        partyService.acceptInvitation(sender, target)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to accept party invitation for {} from {}", sender.getUniqueId(), target.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.SUCCESS;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case ALREADY_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_ALREADY_IN_PARTY));
                        case PLAYER_NOT_FOUND ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        case NO_INVITATION_FOUND ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ACCEPT_ERROR_NO_INVITATION,
                                        Placeholder.unparsed("target", targetName)));
                        case PARTY_FULL ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ACCEPT_ERROR_PARTY_FULL));
                        case JOINED_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ACCEPT_SUCCESS,
                                        Placeholder.unparsed("target", targetName)));
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
