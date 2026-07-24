package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.model.GuildResult;
import com.zornus.guilds.proxy.model.GuildSettings;
import com.zornus.guilds.proxy.service.GuildService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class GuildSettingsCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildSettingsCommand.class);
    private static final SuggestionProvider<CommandSource> INVITE_PRIVACY_SUGGESTIONS = (context, builder) ->
            builder.suggest("all").suggest("friend").suggest("none").buildFuture();

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("settings")
                .executes(context -> handleDisplaySettings(context, guildService))
                .then(BrigadierCommand
                        .literalArgumentBuilder("chat")
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("value", BoolArgumentType.bool())
                                .executes(context -> handleUpdateSetting(
                                        context, guildService, "chat",
                                        String.valueOf(BoolArgumentType.getBool(context, "value"))))
                        )
                )
                .then(BrigadierCommand
                        .literalArgumentBuilder("invites")
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("value", StringArgumentType.word())
                                .suggests(INVITE_PRIVACY_SUGGESTIONS)
                                .executes(context -> handleUpdateSetting(
                                        context, guildService, "invites",
                                        StringArgumentType.getString(context, "value")))
                        )
                );
    }

    private static int handleDisplaySettings(@NonNull CommandContext<CommandSource> context,
                                             GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        guildService.getSettings(sender.getUniqueId())
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get guild settings for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                })
                .thenAccept(settings -> {
                    if (settings != null) {
                        displaySettings(sender, settings);
                    }
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void displaySettings(@NonNull Player sender, @NonNull GuildSettings settings) {
        List<Component> entries = List.of(
                StringUtils.deserialize(
                        SharedConstants.BULLET_POINT + GuildProxyConstants.SETTINGS_DISPLAY_INVITES,
                        Placeholder.unparsed("value", settings.invitePrivacy())),
                StringUtils.deserialize(
                        SharedConstants.BULLET_POINT + GuildProxyConstants.SETTINGS_DISPLAY_CHAT,
                        Placeholder.unparsed("value", String.valueOf(settings.showChat())))
        );
        sender.sendMessage(Component.join(JoinConfiguration.newlines(), entries));
    }

    private static int handleUpdateSetting(@NonNull CommandContext<CommandSource> context,
                                           GuildService guildService, String setting, String value) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        guildService.updateSettings(sender, setting, value)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to update guild setting {} for player {}",
                            setting, sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return GuildResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case SETTING_UPDATED -> {
                            TagResolver resolver = TagResolver.resolver(
                                    Placeholder.unparsed("setting", setting),
                                    Placeholder.unparsed("value", value)
                            );
                            sender.sendMessage(StringUtils.deserialize(
                                    GuildProxyConstants.SETTINGS_UPDATE_SUCCESS, resolver));
                        }
                        case INVALID_SETTING ->
                                sender.sendMessage(StringUtils.deserialize(GuildProxyConstants.USAGE_SETTINGS));
                        case ERROR_ALREADY_HANDLED -> {
                        }
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
