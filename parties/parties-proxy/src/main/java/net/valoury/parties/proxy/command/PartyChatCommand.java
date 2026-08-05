package net.valoury.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.PartyResult;
import net.valoury.parties.proxy.service.PartyService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for sending messages to party members.
 */
public final class PartyChatCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyChatCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return createCommand("chat", partyService);
    }

    public static @NonNull BrigadierCommand createShortcut(PartyService partyService) {
        return new BrigadierCommand(createCommand("pc", partyService)
                .requires(source -> source instanceof Player));
    }

    private static LiteralArgumentBuilder<CommandSource> createCommand(String commandName, PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder(commandName)
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(PartyProxyConstants.USAGE_CHAT));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(context -> handlePartyChat(context, partyService))
                );
    }

    private static int handlePartyChat(@NonNull CommandContext<CommandSource> context, PartyService partyService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String message = StringArgumentType.getString(context, "message");

        partyService.sendPartyChat(sender, message)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case MESSAGE_TOO_LONG ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_MESSAGE_TOO_LONG,
                                        Placeholder.unparsed("max_length", String.valueOf(PartyProxyConstants.MAX_MESSAGE_LENGTH))));
                        case NOT_IN_PARTY ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.CHAT_ERROR_NOT_IN_PARTY));
                        case CHAT_DISABLED ->
                                sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.ERROR_CHAT_DISABLED));
                        case CHAT_SENT -> {
                            // Message already sent by the service, nothing more to do
                        }
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send party chat from player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
