package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.FriendSettings;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Command for managing friend settings.
 */
public final class FriendSettingsCommand {

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService) {
        return BrigadierCommand
                .literalArgumentBuilder("settings")
                .executes(context -> handleDisplaySettings(context, friendService))
                .then(createSettingBranch("messaging", friendService))
                .then(createSettingBranch("jumping", friendService))
                .then(createSettingBranch("lastseen", friendService))
                .then(createSettingBranch("location", friendService))
                .then(createSettingBranch("requests", friendService));
    }

    private static LiteralArgumentBuilder<CommandSource> createSettingBranch(String setting, FriendService friendService) {
        return BrigadierCommand
                .literalArgumentBuilder(setting)
                .then(BrigadierCommand
                        .requiredArgumentBuilder("value", BoolArgumentType.bool())
                        .executes(context -> handleUpdateSetting(context, friendService, setting))
                );
    }

    private static int handleUpdateSetting(@NonNull CommandContext<CommandSource> context, FriendService friendService, String setting) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        boolean value = BoolArgumentType.getBool(context, "value");

        friendService.updateSetting(sender.getUniqueId(), setting, value).thenAccept(result -> {
            switch (result) {
                case SETTING_UPDATED ->
                        sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.SETTINGS_UPDATE_SUCCESS,
                                TagResolver.resolver(
                                        Placeholder.unparsed("setting", setting),
                                        Placeholder.unparsed("value", String.valueOf(value))
                                )));
                case INVALID_SETTING ->
                        sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.ERROR_INVALID_SETTING, Placeholder.unparsed("setting", setting)));
                default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int handleDisplaySettings(@NonNull CommandContext<CommandSource> context, FriendService friendService) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        friendService.getSettings(sender.getUniqueId()).thenAccept(settingsOptional -> {
            FriendSettings settings = settingsOptional.orElse(new FriendSettings(sender.getUniqueId()));

            ComponentBuilder<TextComponent, TextComponent.Builder> messageBuilder = Component.text().appendNewline();

            List<Component> settingEntries = new ArrayList<>();
            settingEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + FriendProxyConstants.SETTINGS_DISPLAY_ALLOW_REQUESTS,
                    Placeholder.unparsed("value", String.valueOf(settings.allowRequests()))
            ));
            settingEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + FriendProxyConstants.SETTINGS_DISPLAY_ALLOW_MESSAGES,
                    Placeholder.unparsed("value", String.valueOf(settings.allowMessages()))
            ));
            settingEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + FriendProxyConstants.SETTINGS_DISPLAY_ALLOW_JUMP,
                    Placeholder.unparsed("value", String.valueOf(settings.allowJump()))
            ));
            settingEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + FriendProxyConstants.SETTINGS_DISPLAY_SHOW_LOCATION,
                    Placeholder.unparsed("value", String.valueOf(settings.showLocation()))
            ));
            settingEntries.add(StringUtils.deserialize(
                    SharedConstants.BULLET_POINT + FriendProxyConstants.SETTINGS_DISPLAY_SHOW_LAST_SEEN,
                    Placeholder.unparsed("value", String.valueOf(settings.showLastSeen()))
            ));

            messageBuilder.append(Component.join(JoinConfiguration.newlines(), settingEntries));
            messageBuilder.appendNewline();

            sender.sendMessage(messageBuilder.build());
        });

        return Command.SINGLE_SUCCESS;
    }

}
