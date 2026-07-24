package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
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

public final class GuildColorCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildColorCommand.class);
    private static final SuggestionProvider<CommandSource> COLOR_SUGGESTIONS = (context, builder) ->
            builder.suggest("white").suggest("gray").suggest("gold").suggest("yellow")
                    .suggest("green").suggest("aqua").suggest("blue").suggest("red")
                    .suggest("light_purple").buildFuture();

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("color")
                .executes(context -> {
                    context.getSource().sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_COLOR));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand
                        .requiredArgumentBuilder("color", StringArgumentType.word())
                        .suggests(COLOR_SUGGESTIONS)
                        .executes(context -> handleGuildColor(context, guildService))
                );
    }

    private static int handleGuildColor(@NonNull CommandContext<CommandSource> context,
                                        GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String guildColor = StringArgumentType.getString(context, "color").toLowerCase();
        guildService.updateGuildColor(sender, guildColor)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to update guild color for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return GuildResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case GUILD_COLOR_UPDATED -> sender.sendMessage(StringUtils.deserialize(
                                GuildProxyConstants.COLOR_SUCCESS,
                                Placeholder.parsed("colored_value", "<" + guildColor + ">" + guildColor)));
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_IN_GUILD));
                        case NOT_LEADER ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_NOT_LEADER));
                        case INVALID_GUILD_COLOR ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.ERROR_INVALID_GUILD_COLOR));
                        case ERROR_ALREADY_HANDLED -> {
                        }
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
