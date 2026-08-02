package net.valoury.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.GuildGroupSettings;
import net.valoury.guilds.proxy.model.GuildSettings;
import net.valoury.guilds.proxy.service.GuildService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GuildSettingsCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildSettingsCommand.class);
    private static final SuggestionProvider<CommandSource> INVITE_PRIVACY_SUGGESTIONS = (context, builder) ->
            builder.suggest("all").suggest("friend").suggest("none").buildFuture();
    private static final SuggestionProvider<CommandSource> PRIVACY_SUGGESTIONS = (context, builder) ->
            builder.suggest("private").suggest("public").buildFuture();

    public static LiteralArgumentBuilder<CommandSource> create(GuildService guildService) {
        return BrigadierCommand
                .literalArgumentBuilder("settings")
                .executes(context -> handleDisplaySettings(context, guildService))
                .then(BrigadierCommand
                        .literalArgumentBuilder("chat")
                        .executes(GuildSettingsCommand::sendUsage)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("value", BoolArgumentType.bool())
                                .executes(context -> handleUpdateSetting(
                                        context, guildService, "chat",
                                        String.valueOf(BoolArgumentType.getBool(context, "value"))))
                        )
                )
                .then(BrigadierCommand
                        .literalArgumentBuilder("invites")
                        .executes(GuildSettingsCommand::sendUsage)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("value", StringArgumentType.word())
                                .suggests(INVITE_PRIVACY_SUGGESTIONS)
                                .executes(context -> handleUpdateSetting(
                                        context, guildService, "invites",
                                        StringArgumentType.getString(context, "value")))
                        )
                )
                .then(BrigadierCommand
                        .literalArgumentBuilder("privacy")
                        .executes(GuildSettingsCommand::sendUsage)
                        .then(BrigadierCommand
                                .requiredArgumentBuilder("value", StringArgumentType.word())
                                .suggests(PRIVACY_SUGGESTIONS)
                                .executes(context -> handleUpdateSetting(
                                        context, guildService, "privacy",
                                        StringArgumentType.getString(context, "value")))
                        )
                );
    }

    private static int sendUsage(@NonNull CommandContext<CommandSource> context) {
        context.getSource().sendMessage(StringUtils.deserialize(
                GuildProxyConstants.USAGE_SETTINGS));
        return Command.SINGLE_SUCCESS;
    }

    private static int handleDisplaySettings(@NonNull CommandContext<CommandSource> context,
                                             GuildService guildService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        guildService.getSettings(sender.getUniqueId())
                .thenCombine(
                        guildService.getGroupSettingsForPlayer(sender.getUniqueId()),
                        SettingsView::new
                )
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get guild settings for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                })
                .thenAccept(settingsView -> {
                    if (settingsView != null) {
                        displaySettings(sender, settingsView);
                    }
                });

        return Command.SINGLE_SUCCESS;
    }

    private static void displaySettings(@NonNull Player sender, @NonNull SettingsView settingsView) {
        GuildSettings settings = settingsView.personalSettings();
        List<Component> entries = new ArrayList<>();
        String privacyDisplayValue = settingsView.groupSettings()
                .map(groupSettings -> groupSettings.joinPolicy().storedValue())
                .orElse("not in a guild");
        entries.add(StringUtils.deserialize(
                SharedConstants.BULLET_POINT + GuildProxyConstants.SETTINGS_DISPLAY_PRIVACY,
                Placeholder.unparsed("value", privacyDisplayValue)));
        entries.add(StringUtils.deserialize(
                SharedConstants.BULLET_POINT + GuildProxyConstants.SETTINGS_DISPLAY_INVITES,
                Placeholder.unparsed("value", settings.invitePrivacy())));
        entries.add(StringUtils.deserialize(
                SharedConstants.BULLET_POINT + GuildProxyConstants.SETTINGS_DISPLAY_CHAT,
                Placeholder.unparsed("value", String.valueOf(settings.showChat()))));

        TextComponent.Builder messageBuilder = Component.text().appendNewline();
        messageBuilder.append(Component.join(JoinConfiguration.newlines(), entries));
        messageBuilder.appendNewline();

        sender.sendMessage(messageBuilder.build());
    }

    private static int handleUpdateSetting(@NonNull CommandContext<CommandSource> context,
                                           GuildService guildService, String setting, String value) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        guildService.updateSettings(sender, setting, value)
                .thenAccept(result -> {
                    switch (result.legacy()) {
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
                        case NOT_IN_GUILD ->
                                sender.sendMessage(StringUtils.deserialize(
                                        GuildProxyConstants.ERROR_NOT_IN_GUILD));
                        case INSUFFICIENT_RANK ->
                                sender.sendMessage(StringUtils.deserialize(
                                        GuildProxyConstants.ERROR_INSUFFICIENT_RANK));
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to update guild setting {} for player {}",
                            setting, sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private record SettingsView(
            @NonNull GuildSettings personalSettings,
            @NonNull Optional<GuildGroupSettings> groupSettings
    ) {
    }
}
