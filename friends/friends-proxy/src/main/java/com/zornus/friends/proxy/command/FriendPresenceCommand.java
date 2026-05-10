package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.PresenceState;
import com.zornus.friends.proxy.model.result.FriendResult;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for managing online/offline presence state visibility.
 */
public final class FriendPresenceCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendPresenceCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService) {
        return BrigadierCommand
                .literalArgumentBuilder("presence")
                .requires(source -> source instanceof Player)
                .executes(context -> handleDisplayPresence(context, friendService))
                .then(BrigadierCommand
                        .literalArgumentBuilder("online")
                        .executes(context -> handleUpdatePresence(context, friendService, PresenceState.ONLINE))
                )
                .then(BrigadierCommand
                        .literalArgumentBuilder("offline")
                        .executes(context -> handleUpdatePresence(context, friendService, PresenceState.OFFLINE))
                );
    }

    private static int handleDisplayPresence(@NonNull CommandContext<CommandSource> context, FriendService friendService) {
        if (!(context.getSource() instanceof Player sender)) {
            return Command.SINGLE_SUCCESS;
        }

        friendService.getSettings(sender.getUniqueId())
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to get settings for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                })
                .thenAccept(settings -> {
                    if (settings == null) return;
            PresenceState presenceState = settings.presenceState();
            String currentPresence = presenceState.name().toLowerCase();

            sender.sendMessage(StringUtils.deserialize(
                    FriendProxyConstants.PRESENCE_DISPLAY,
                    Placeholder.unparsed("presence", currentPresence)
            ));
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int handleUpdatePresence(@NonNull CommandContext<CommandSource> context, @NonNull FriendService friendService, PresenceState presenceState) {
        if (!(context.getSource() instanceof Player sender)) {
            return Command.SINGLE_SUCCESS;
        }

        friendService.setPresence(sender.getUniqueId(), presenceState)
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to set presence for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return FriendResult.ERROR_ALREADY_HANDLED;
                })
                .thenAccept(result -> {
            switch (result) {
                case STATUS_UPDATED ->
                        sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.PRESENCE_UPDATE_SUCCESS, Placeholder.unparsed("presence", presenceState.name().toLowerCase())));
                case ERROR_ALREADY_HANDLED -> {}
                default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
