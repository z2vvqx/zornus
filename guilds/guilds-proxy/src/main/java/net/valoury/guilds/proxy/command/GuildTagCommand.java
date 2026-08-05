package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildTagCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildTagCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("tag")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_TAG));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("new_tag", StringArgumentType.word())
                        .executes(context -> handleGuildTag(context, guildService))
                );
    }

    private static int handleGuildTag(@NonNull CommandContext<CommandSource> context,
                                      GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String guildTag = StringArgumentType.getString(context, "new_tag");
        guildService.updateGuildTag(sender, guildTag)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case GUILD_TAG_UPDATED -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.TAG_SUCCESS, Placeholder.unparsed("new_tag", guildTag)));
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.TAG_ERROR_NOT_IN_GUILD));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_LEADER));
                        case INVALID_GUILD_TAG ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_INVALID_GUILD_TAG));
                        case GUILD_TAG_ALREADY_EXISTS ->
                                sender.sendMessage(StringUtils.deserialize(
                                        GuildProxyConstants.ERROR_GUILD_TAG_ALREADY_EXISTS));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to update guild tag for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
