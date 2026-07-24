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

public final class GuildRevokeCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildRevokeCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("revoke")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_UNINVITE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("player_name", StringArgumentType.word())
                        .executes(context -> handleRevokeInvitation(context, guildService))
                );
    }

    private static int handleRevokeInvitation(@NonNull CommandContext<CommandSource> context,
                                              GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = StringArgumentType.getString(context, "player_name");
        guildService.revokeInvitation(sender, targetName)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to revoke guild invitation for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return GuildResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case INVITATION_REVOKED -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.UNINVITE_SUCCESS,
                                Placeholder.unparsed("target", targetName)));
                        case NO_INVITATION_FOUND -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.UNINVITE_ERROR_NO_INVITATION,
                                Placeholder.unparsed("target", targetName)));
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.UNINVITE_ERROR_NOT_IN_GUILD));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_LEADER));
                        case PLAYER_NOT_FOUND ->
                                sender.sendMessage(StringUtils.deserialize(SharedConstants.PLAYER_NOT_FOUND));
                        case ERROR_ALREADY_HANDLED -> {
                        }
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
