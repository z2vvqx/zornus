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
 * Command for kicking members from party.
 */
public final class PartyKickCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyKickCommand.class);

    private static final SuggestionProvider<CommandSource> PLAYER_SUGGESTIONS = (context, builder) -> {
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService, ProxyServer proxyServer) {
        return BrigadierCommand
                .literalArgumentBuilder("kick")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PartyProxyConstants.USAGE_KICK));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("member_name", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(context -> handleKickMember(context, partyService, proxyServer, null))
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String reason = StringArgumentType.getString(context, "reason");
                                    return handleKickMember(context, partyService, proxyServer, reason);
                                })
                        )
                );
    }

    private static int handleKickMember(@NonNull CommandContext<CommandSource> context, PartyService partyService,
                                        ProxyServer proxyServer, String reason) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "member_name");

        Optional<Player> targetOptional = proxyServer.getPlayer(targetName);
        if (targetOptional.isEmpty()) {
            sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            return Command.SINGLE_SUCCESS;
        }
        Player target = targetOptional.get();

        partyService.kickMember(sender, target, reason)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to kick member {} from party by {}", target.getUniqueId(), sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.KICK_ERROR_NOT_IN_PARTY));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_LEADER));
                        case PLAYER_NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.KICK_ERROR_PLAYER_NOT_IN_PARTY,
                                        Placeholder.unparsed("target", targetName)));
                        case CANNOT_KICK_SELF ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.KICK_ERROR_CANNOT_KICK_SELF));
                        case MEMBER_KICKED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.KICK_SUCCESS,
                                        Placeholder.unparsed("target", targetName)));
                        case ERROR_ALREADY_HANDLED -> {}
                        default ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
