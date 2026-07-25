package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildCreateCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildCreateCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("create")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_CREATE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("name", StringArgumentType.word())
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("tag", StringArgumentType.word())
                                .executes(context -> handleCreateGuild(context, guildService))
                        )
                );
    }

    private static int handleCreateGuild(@NonNull CommandContext<CommandSource> context,
                                         GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String guildName = StringArgumentType.getString(context, "name");
        String guildTag = StringArgumentType.getString(context, "tag");

        guildService.createGuild(sender, guildName, guildTag, "<white>")
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case GUILD_CREATED -> {
                            TagResolver resolver = TagResolver.resolver(
                                    Placeholder.unparsed("guild_name", guildName),
                                    Placeholder.unparsed("guild_tag", guildTag)
                            );
                            sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.CREATE_SUCCESS, resolver));
                        }
                        case ALREADY_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_ALREADY_IN_GUILD));
                        case INVALID_GUILD_NAME ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_INVALID_GUILD_NAME));
                        case INVALID_GUILD_TAG ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_INVALID_GUILD_TAG));
                        case NAME_ALREADY_EXISTS ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.RENAME_ERROR_NAME_EXISTS));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to create guild for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
