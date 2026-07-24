package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.model.GuildResult;
import com.zornus.guilds.proxy.service.GuildService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildChatCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildChatCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("chat")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_CHAT));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("message_array", StringArgumentType.greedyString())
                        .executes(context -> handleGuildChat(context, guildService))
                );
    }

    private static int handleGuildChat(@NonNull CommandContext<CommandSource> context,
                                       GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String message = StringArgumentType.getString(context, "message_array");
        guildService.sendGuildChat(sender, message)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to send guild chat message for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return GuildResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case CHAT_SENT -> {
                        }
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_IN_GUILD));
                        case CHAT_DISABLED ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_CHAT_DISABLED));
                        case MESSAGE_TOO_LONG -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.ERROR_MESSAGE_TOO_LONG,
                                Placeholder.unparsed("max_length",
                                        String.valueOf(GuildProxyConstants.MAX_MESSAGE_LENGTH))));
                        case ERROR_ALREADY_HANDLED -> {
                        }
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
