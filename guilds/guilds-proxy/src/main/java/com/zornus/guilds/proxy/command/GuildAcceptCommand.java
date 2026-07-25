package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.service.GuildService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildAcceptCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildAcceptCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("accept")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_ACCEPT));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("guild_name", StringArgumentType.word())
                        .executes(context -> handleAcceptInvitation(context, guildService))
                );
    }

    private static int handleAcceptInvitation(@NonNull CommandContext<CommandSource> context,
                                              GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String guildName = StringArgumentType.getString(context, "guild_name");
        guildService.acceptInvitation(sender, guildName)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case JOINED_GUILD -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.ACCEPT_SUCCESS,
                                Placeholder.unparsed("guild_name", guildName)));
                        case NO_INVITATION_FOUND -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.ACCEPT_ERROR_NO_INVITATION,
                                Placeholder.unparsed("guild_name", guildName)));
                        case GUILD_FULL ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ACCEPT_ERROR_GUILD_FULL));
                        case ALREADY_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_ALREADY_IN_GUILD));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to accept guild invitation for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
