package com.zornus.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.model.PartyResult;
import com.zornus.parties.proxy.model.PartySettings;
import com.zornus.parties.proxy.service.PartyService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
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

/**
 * Command for managing party settings.
 */
public final class PartySettingsCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartySettingsCommand.class);

    private static final SuggestionProvider<CommandSource> INVITE_PRIVACY_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("all").suggest("friend").suggest("none").buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("settings")
                .executes(context -> handleDisplaySettings(context, partyService))
                .then(createBooleanSettingBranch("warp", partyService))
                .then(createBooleanSettingBranch("chat", partyService))
                .then(createInvitePrivacyBranch(partyService));
    }

    private static int handleDisplaySettings(@NonNull CommandContext<CommandSource> context, PartyService partyService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        partyService.getSettings(sender.getUniqueId()).thenAccept(settings -> {
            TextComponent.Builder messageBuilder = Component.text().appendNewline();

            List<Component> settingEntries = new ArrayList<>();
            // Privacy/Security settings first
            settingEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + PartyProxyConstants.SETTINGS_DISPLAY_INVITES,
                    Placeholder.unparsed("value", settings.invitePrivacy())
            ));
            // Communication settings
            settingEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + PartyProxyConstants.SETTINGS_DISPLAY_CHAT,
                    Placeholder.unparsed("value", String.valueOf(settings.allowChat()))
            ));
            // Interaction settings
            settingEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + PartyProxyConstants.SETTINGS_DISPLAY_WARP,
                    Placeholder.unparsed("value", String.valueOf(settings.allowWarp()))
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
                .then(BrigadierCommand
                        .requiredArgumentBuilder("value", BoolArgumentType.bool())
                        .executes(context -> handleUpdateBooleanSetting(context, partyService, setting))
                );
    }

    private static LiteralArgumentBuilder<CommandSource> createInvitePrivacyBranch(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("invites")
                .then(BrigadierCommand
                        .requiredArgumentBuilder("value", StringArgumentType.word())
                        .suggests(INVITE_PRIVACY_SUGGESTIONS)
                        .executes(context -> handleUpdateInvitePrivacy(context, partyService))
                );
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
            default -> setting;
        };

        partyService.updateBooleanSetting(sender.getUniqueId(), settingName, value)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to update setting {} for player {}", setting, sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case SETTING_UPDATED -> {
                            TagResolver resolver = TagResolver.builder()
                                    .resolver(Placeholder.unparsed("setting", setting))
                                    .resolver(Placeholder.unparsed("value", String.valueOf(value)))
                                    .build();
                            sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.SETTINGS_UPDATE_SUCCESS, resolver));
                        }
                        case ERROR_ALREADY_HANDLED -> {}
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
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
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to update invite privacy for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return PartyResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
                    switch (result) {
                        case SETTING_UPDATED -> {
                            TagResolver resolver = TagResolver.builder()
                                    .resolver(Placeholder.unparsed("setting", "invites"))
                                    .resolver(Placeholder.unparsed("value", value))
                                    .build();
                            sender.sendMessage(StringUtils.deserialize(PartyProxyConstants.SETTINGS_UPDATE_SUCCESS, resolver));
                        }
                        case ERROR_ALREADY_HANDLED -> {}
                        default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    }
                });

        return Command.SINGLE_SUCCESS;
    }
}
