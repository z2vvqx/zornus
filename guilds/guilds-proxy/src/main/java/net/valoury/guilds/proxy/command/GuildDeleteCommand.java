package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildDeleteCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildDeleteCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("delete")
                .executes(context -> handleDeleteGuild(context, guildService, false))
                .then(BrigadierCommand
                        .literalArgumentBuilder("confirm")
                        .executes(context -> handleDeleteGuild(context, guildService, true))
                );
    }

    private static int handleDeleteGuild(@NonNull CommandContext<CommandSource> context,
                                         GuildService guildService, boolean isConfirming) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        guildService.disbandGuild(sender, isConfirming)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case GUILD_DISBANDED ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.DISBAND_SUCCESS));
                        case DISBAND_CONFIRMATION_REQUIRED ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.DISBAND_CONFIRMATION_REQUIRED));
                        case NO_CONFIRMATION_PENDING ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.DISBAND_ERROR_NO_CONFIRMATION));
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_IN_GUILD));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_LEADER));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to delete guild for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
