package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.service.GuildService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildLeaveCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildLeaveCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("leave")
                .executes(context -> handleLeaveGuild(context, guildService));
    }

    private static int handleLeaveGuild(@NonNull CommandContext<CommandSource> context,
                                        GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        guildService.leaveGuild(sender)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case LEFT_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.LEAVE_SUCCESS));
                        case LEFT_GUILD_DISBANDED ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.LEAVE_SUCCESS_DISBANDED));
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.LEAVE_ERROR_NOT_IN_GUILD));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to leave guild for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
