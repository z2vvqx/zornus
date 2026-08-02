package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.result.GuildResults;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildJoinCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildJoinCommand.class);

    private GuildJoinCommand() {
    }

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("join")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(
                            GuildProxyConstants.USAGE_JOIN));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("guild_name", StringArgumentType.word())
                        .executes(context -> handleJoin(context, guildService))
                );
    }

    private static int handleJoin(
            @NonNull CommandContext<CommandSource> context,
            GuildService guildService
    ) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String guildName = StringArgumentType.getString(context, "guild_name");
        guildService.joinPublicGuild(sender, guildName)
                .thenAccept(result -> {
                    switch (result) {
                        case GuildResults.JoinPublic.Joined joined ->
                                sender.sendMessage(StringUtils.deserialize(
                                        GuildProxyConstants.JOIN_SUCCESS,
                                        Placeholder.unparsed("guild_name", joined.guildName())
                                ));
                        case GuildResults.JoinPublic.GuildNotFound ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        GuildProxyConstants.JOIN_ERROR_NOT_FOUND));
                        case GuildResults.JoinPublic.GuildPrivate ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        GuildProxyConstants.JOIN_ERROR_PRIVATE));
                        case GuildResults.JoinPublic.GuildFull ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        GuildProxyConstants.ACCEPT_ERROR_GUILD_FULL));
                        case GuildResults.JoinPublic.AlreadyInGuild ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        GuildProxyConstants.ERROR_ALREADY_IN_GUILD));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error(
                            "Failed to join public guild {} for player {}",
                            guildName,
                            sender.getUniqueId(),
                            throwable
                    );
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
