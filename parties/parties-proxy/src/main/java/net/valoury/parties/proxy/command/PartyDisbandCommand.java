package net.valoury.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.PartyResult;
import net.valoury.parties.proxy.service.PartyService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for disbanding party.
 */
public final class PartyDisbandCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyDisbandCommand.class);
    private static final SuggestionProvider<CommandSource> CONFIRMATION_SUGGESTIONS =
            (context, builder) -> {
                if ("confirm".startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest("confirm");
                }
                return builder.buildFuture();
            };

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("disband")
                .executes(context -> handleDisbandParty(context, partyService, false))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("confirmation", StringArgumentType.word())
                        .suggests(CONFIRMATION_SUGGESTIONS)
                        .executes(context -> {
                            String confirmation = StringArgumentType.getString(context, "confirmation");
                            return handleDisbandParty(context, partyService, "confirm".equalsIgnoreCase(confirmation));
                        })
                );
    }

    private static int handleDisbandParty(@NonNull CommandContext<CommandSource> context, PartyService partyService, boolean isConfirming) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        partyService.disbandParty(sender, isConfirming)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.DISBAND_ERROR_NOT_IN_PARTY));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_NOT_LEADER));
                        case DISBAND_CONFIRMATION_REQUIRED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.DISBAND_CONFIRMATION_REQUIRED));
                        case NO_CONFIRMATION_PENDING ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.DISBAND_ERROR_NO_CONFIRMATION));
                        case PARTY_DISBANDED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.DISBAND_SUCCESS));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to disband party for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
