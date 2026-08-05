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

public final class PartyModeratorCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyModeratorCommand.class);

    private PartyModeratorCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> createPromotion(
            PartyService partyService,
            ProxyServer proxyServer
    ) {
        return create("promote", true, partyService, proxyServer);
    }

    public static LiteralArgumentBuilder<CommandSource> createDemotion(
            PartyService partyService,
            ProxyServer proxyServer
    ) {
        return create("demote", false, partyService, proxyServer);
    }

    private static LiteralArgumentBuilder<CommandSource> create(
            @NonNull String commandName,
            boolean promotion,
            PartyService partyService,
            ProxyServer proxyServer
    ) {
        return BrigadierCommand
                .literalArgumentBuilder(commandName)
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(
                            promotion
                                    ? PartyProxyConstants.USAGE_PROMOTE
                                    : PartyProxyConstants.USAGE_DEMOTE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("member_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleRoleChange(
                                context,
                                partyService,
                                proxyServer,
                                promotion
                        ))
                );
    }

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

    private static int handleRoleChange(
            @NonNull CommandContext<CommandSource> context,
            PartyService partyService,
            ProxyServer proxyServer,
            boolean promotion
    ) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String memberName = StringArgumentType.getString(context, "member_name");
        Player target = proxyServer.getPlayer(memberName).orElse(null);
        var resultFuture = promotion
                ? partyService.promoteModerator(sender, target)
                : partyService.demoteModerator(sender, target);
        resultFuture.thenAccept(result -> handleResult(sender, result, promotion))
                .exceptionally(throwable -> {
                    LOGGER.error(
                            "Failed to {} party moderator {} for player {}",
                            promotion ? "promote" : "demote",
                            memberName,
                            sender.getUniqueId(),
                            throwable
                    );
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void handleResult(
            @NonNull Player sender,
            PartyResults.ChangeModeratorRole result,
            boolean promotion
    ) {
        switch (result) {
            case PartyResults.ChangeModeratorRole.Changed changed ->
                    sender.sendMessage(StringUtils.deserialize(
                            promotion
                                    ? PartyProxyConstants.PROMOTE_SUCCESS
                                    : PartyProxyConstants.DEMOTE_SUCCESS,
                            Placeholder.unparsed("target", changed.memberName())
                    ));
            case PartyResults.ChangeModeratorRole.PlayerNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
            case PartyResults.ChangeModeratorRole.NotInParty ignored ->
                    sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_IN_PARTY));
            case PartyResults.ChangeModeratorRole.NotLeader ignored ->
                    sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_LEADER));
            case PartyResults.ChangeModeratorRole.MemberNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            PartyProxyConstants.ROLE_ERROR_PLAYER_NOT_IN_PARTY));
            case PartyResults.ChangeModeratorRole.CannotChangeLeader ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            PartyProxyConstants.ROLE_ERROR_CANNOT_CHANGE_LEADER));
            case PartyResults.ChangeModeratorRole.AlreadyModerator ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            PartyProxyConstants.PROMOTE_ERROR_ALREADY_MODERATOR));
            case PartyResults.ChangeModeratorRole.NotModerator ignored ->
                    sender.sendMessage(StringUtils.deserialize(
                            PartyProxyConstants.DEMOTE_ERROR_NOT_MODERATOR));
            case PartyResults.ChangeModeratorRole.PartyNotFound ignored ->
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
        }
    }
}
