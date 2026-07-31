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
import net.valoury.parties.proxy.service.PartyService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;

/**
 * Command for revoking party invitations.
 */
public final class PartyUninviteCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyUninviteCommand.class);

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
                .literalArgumentBuilder("uninvite")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PartyProxyConstants.USAGE_UNINVITE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .suggests(onlinePlayerSuggestions(proxyServer))
                        .executes(context -> handleUninvitePlayer(context, partyService, proxyServer))
                );
    }

    private static int handleUninvitePlayer(@NonNull CommandContext<CommandSource> context, PartyService partyService,
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
        String targetUsername = target.getUsername();

        partyService.revokeInvitation(sender, target)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.UNINVITE_ERROR_NOT_IN_PARTY));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_LEADER));
                        case NO_INVITATION_FOUND ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.UNINVITE_ERROR_NO_INVITATION,
                                        Placeholder.unparsed("target", targetUsername)));
                        case INVITATION_REVOKED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.UNINVITE_SUCCESS,
                                        Placeholder.unparsed("target", targetUsername)));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to revoke party invitation for {} to {}",
                            sender.getUniqueId(), target.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
