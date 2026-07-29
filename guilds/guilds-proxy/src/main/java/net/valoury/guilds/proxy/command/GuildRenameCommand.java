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
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildRenameCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildRenameCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("rename")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_RENAME));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("new_name", StringArgumentType.word())
                        .executes(context -> handleRenameGuild(context, guildService, false))
                        .then(BrigadierCommand
                                .literalArgumentBuilder("confirm")
                                .executes(context -> handleRenameGuild(context, guildService, true))
                        )
                );
    }

    private static int handleRenameGuild(@NonNull CommandContext<CommandSource> context,
                                         GuildService guildService, boolean isConfirming) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String newName = StringArgumentType.getString(context, "new_name");
        guildService.renameGuild(sender, newName, isConfirming)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case GUILD_RENAMED -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.RENAME_SUCCESS,
                                Placeholder.unparsed("new_name", newName)));
                        case RENAME_CONFIRMATION_REQUIRED -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.RENAME_CONFIRMATION_REQUIRED,
                                Placeholder.unparsed("new_name", newName)));
                        case NO_CONFIRMATION_PENDING ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.RENAME_ERROR_NO_CONFIRMATION));
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.RENAME_ERROR_NOT_IN_GUILD));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_LEADER));
                        case INVALID_GUILD_NAME ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_INVALID_GUILD_NAME));
                        case NAME_ALREADY_EXISTS ->
                                sender.sendMessage(StringUtils.deserialize(
                                        GuildProxyConstants.ERROR_GUILD_NAME_ALREADY_EXISTS));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to rename guild for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
