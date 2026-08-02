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
import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.result.PartyResults;
import net.valoury.parties.proxy.service.PartyService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class PartyJoinCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyJoinCommand.class);

    private PartyJoinCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> create(
            PartyService partyService,
            ProxyServer proxyServer
    ) {
        return BrigadierCommand
                .literalArgumentBuilder("join")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(
                            PartyProxyConstants.USAGE_JOIN));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("leader_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleJoin(context, partyService, proxyServer))
                );
    }

    private static SuggestionProvider<CommandSource> onlinePlayerSuggestions(
            ProxyServer proxyServer
    ) {
        return (context, builder) -> {
            String remainingInput = builder.getRemainingLowerCase();
            proxyServer.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(username -> username.toLowerCase(Locale.ROOT)
                            .startsWith(remainingInput))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private static int handleJoin(
            @NonNull CommandContext<CommandSource> context,
            PartyService partyService,
            ProxyServer proxyServer
    ) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String leaderName = StringArgumentType.getString(context, "leader_name");
        Player targetLeader = proxyServer.getPlayer(leaderName).orElse(null);
        partyService.joinPublicParty(sender, targetLeader)
                .thenAccept(result -> {
                    switch (result) {
                        case PartyResults.JoinPublic.Joined joined ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.JOIN_SUCCESS,
                                        Placeholder.unparsed("leader", joined.leaderName())
                                ));
                        case PartyResults.JoinPublic.PlayerNotFound ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        SharedConstants.PLAYER_NOT_FOUND));
                        case PartyResults.JoinPublic.TargetNotLeader ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.JOIN_ERROR_NOT_LEADER));
                        case PartyResults.JoinPublic.AlreadyInParty ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.ERROR_ALREADY_IN_PARTY));
                        case PartyResults.JoinPublic.PartyPrivate ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.JOIN_ERROR_PRIVATE));
                        case PartyResults.JoinPublic.PartyFull ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.ACCEPT_ERROR_PARTY_FULL));
                        case PartyResults.JoinPublic.PartyNotFound ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.JOIN_ERROR_NOT_FOUND));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error(
                            "Failed to join public party led by {} for player {}",
                            leaderName,
                            sender.getUniqueId(),
                            throwable
                    );
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
