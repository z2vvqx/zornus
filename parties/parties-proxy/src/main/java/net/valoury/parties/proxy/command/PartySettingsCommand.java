package net.valoury.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.PartyGroupSettings;
import net.valoury.parties.proxy.model.PartySettings;
import net.valoury.parties.proxy.model.result.PartyResults;
import net.valoury.parties.proxy.service.PartyService;
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

/**
 * Command for managing party settings.
 */
public final class PartySettingsCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartySettingsCommand.class);

    private static final SuggestionProvider<CommandSource> INVITE_PRIVACY_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("all").suggest("friend").suggest("none").buildFuture();
    };
    private static final SuggestionProvider<CommandSource> PRIVACY_SUGGESTIONS = (context, builder) ->
            builder.suggest("private").suggest("public").buildFuture();

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("settings")
                .executes(context -> handleDisplaySettings(context, partyService))
                .then(createBooleanSettingBranch("warp", partyService))
                .then(createBooleanSettingBranch("chat", partyService))
                .then(createBooleanSettingBranch("autowarp", partyService))
                .then(createInvitePrivacyBranch(partyService))
                .then(createPrivacyBranch(partyService));
    }

    private static int handleDisplaySettings(@NonNull CommandContext<CommandSource> context, PartyService partyService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        partyService.getSettings(sender.getUniqueId())
                .thenCombine(
                        partyService.getGroupSettingsForPlayer(sender.getUniqueId()),
                        SettingsView::new
                )
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get settings for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                })
                .thenAccept(settingsView -> {
                    if (settingsView == null) return;
                    PartySettings settings = settingsView.personalSettings();

                    TextComponent.Builder messageBuilder = Component.text().appendNewline();

                    List<Component> settingEntries = new ArrayList<>();
                    String privacyDisplayValue = settingsView.groupSettings()
                            .map(groupSettings -> groupSettings.joinPolicy().storedValue())
                            .orElse("not in a party");
                    settingEntries.add(StringUtils.deserialize(
                            SharedConstants.BULLET_POINT + PartyProxyConstants.SETTINGS_DISPLAY_PRIVACY,
                            Placeholder.unparsed("value", privacyDisplayValue)
                    ));
                    settingEntries.add(StringUtils.deserialize(
                            SharedConstants.BULLET_POINT + PartyProxyConstants.SETTINGS_DISPLAY_INVITES,
                            Placeholder.unparsed("value", settings.invitePrivacy())
                    ));
                    settingEntries.add(StringUtils.deserialize(
                            SharedConstants.BULLET_POINT + PartyProxyConstants.SETTINGS_DISPLAY_CHAT,
                            Placeholder.unparsed("value", String.valueOf(settings.allowChat()))
                    ));
                    settingEntries.add(StringUtils.deserialize(
                            SharedConstants.BULLET_POINT + PartyProxyConstants.SETTINGS_DISPLAY_WARP,
                            Placeholder.unparsed("value", String.valueOf(settings.allowWarp()))
                    ));
                    settingEntries.add(StringUtils.deserialize(
                            SharedConstants.BULLET_POINT + PartyProxyConstants.SETTINGS_DISPLAY_AUTO_WARP,
                            Placeholder.unparsed("value", String.valueOf(settings.autoWarp()))
                    ));

                    messageBuilder.append(Component.join(JoinConfiguration.newlines(), settingEntries));
                    messageBuilder.appendNewline();

                    sender.sendMessage(messageBuilder.build());
                });

        return Command.SINGLE_SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSource> createBooleanSettingBranch(String setting, PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder(setting)
                .executes(PartySettingsCommand::sendUsage)
                .then(BrigadierCommand
                        .requiredArgumentBuilder("value", BoolArgumentType.bool())
                        .executes(context -> handleUpdateBooleanSetting(context, partyService, setting))
                );
    }

    private static LiteralArgumentBuilder<CommandSource> createInvitePrivacyBranch(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("invites")
                .executes(PartySettingsCommand::sendUsage)
                .then(BrigadierCommand
                        .requiredArgumentBuilder("value", StringArgumentType.word())
                        .suggests(INVITE_PRIVACY_SUGGESTIONS)
                        .executes(context -> handleUpdateInvitePrivacy(context, partyService))
                );
    }

    private static LiteralArgumentBuilder<CommandSource> createPrivacyBranch(
            PartyService partyService
    ) {
        return BrigadierCommand
                .literalArgumentBuilder("privacy")
                .executes(PartySettingsCommand::sendUsage)
                .then(BrigadierCommand
                        .requiredArgumentBuilder("value", StringArgumentType.word())
                        .suggests(PRIVACY_SUGGESTIONS)
                        .executes(context -> handleUpdatePrivacy(context, partyService))
                );
    }

    private static int sendUsage(@NonNull CommandContext<CommandSource> context) {
        context.getSource().sendMessage(StringUtils.deserialize(
                PartyProxyConstants.USAGE_SETTINGS));
        return Command.SINGLE_SUCCESS;
    }

    private static int handleUpdateBooleanSetting(@NonNull CommandContext<CommandSource> context, PartyService partyService, String setting) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        boolean value = BoolArgumentType.getBool(context, "value");

        String settingName = switch (setting) {
            case "warp" -> "allow_warp";
            case "chat" -> "allow_chat";
            case "autowarp" -> "auto_warp";
            default -> setting;
        };

        partyService.updateBooleanSetting(sender.getUniqueId(), settingName, value)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case SETTING_UPDATED -> {
                            TagResolver resolver = TagResolver.builder()
                                    .resolver(Placeholder.unparsed("setting", setting))
                                    .resolver(Placeholder.unparsed("value", String.valueOf(value)))
                                    .build();
                            sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.SETTINGS_UPDATE_SUCCESS, resolver));
                        }
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to update setting {} for player {}",
                            setting, sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static int handleUpdateInvitePrivacy(@NonNull CommandContext<CommandSource> context, PartyService partyService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String value = StringArgumentType.getString(context, "value");

        partyService.updateInvitePrivacy(sender.getUniqueId(), value)
                .thenAccept(result -> {
                    switch (result.legacy()) {
                        case SETTING_UPDATED -> {
                            TagResolver resolver = TagResolver.builder()
                                    .resolver(Placeholder.unparsed("setting", "invites"))
                                    .resolver(Placeholder.unparsed("value", value))
                                    .build();
                            sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.SETTINGS_UPDATE_SUCCESS, resolver));
                        }
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to update invite privacy for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private static int handleUpdatePrivacy(
            @NonNull CommandContext<CommandSource> context,
            PartyService partyService
    ) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        String value = StringArgumentType.getString(context, "value");
        partyService.updateGroupPrivacy(sender.getUniqueId(), value)
                .thenAccept(result -> {
                    switch (result) {
                        case PartyResults.UpdateSetting.Updated ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.SETTINGS_UPDATE_SUCCESS,
                                        TagResolver.resolver(
                                                Placeholder.unparsed("setting", "privacy"),
                                                Placeholder.unparsed("value", value)
                                        )
                                ));
                        case PartyResults.UpdateSetting.InvalidSetting ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.USAGE_SETTINGS));
                        case PartyResults.UpdateSetting.NotInParty ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.ERROR_NOT_IN_PARTY));
                        case PartyResults.UpdateSetting.NotLeader ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        PartyProxyConstants.ERROR_NOT_LEADER));
                        case PartyResults.UpdateSetting.PartyNotFound ignored ->
                                sender.sendMessage(StringUtils.deserialize(
                                        SharedConstants.ERROR_UNEXPECTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error(
                            "Failed to update party join policy for player {}",
                            sender.getUniqueId(),
                            throwable
                    );
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }

    private record SettingsView(
            @NonNull PartySettings personalSettings,
            @NonNull Optional<PartyGroupSettings> groupSettings
    ) {
    }
}
