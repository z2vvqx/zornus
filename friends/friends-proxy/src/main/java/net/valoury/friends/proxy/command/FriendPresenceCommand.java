package net.valoury.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.friends.proxy.FriendProxyConstants;
import net.valoury.friends.proxy.model.PresenceState;
import net.valoury.friends.proxy.model.result.SetPresenceResult;
import net.valoury.friends.proxy.service.FriendService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
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
                .thenAccept(result -> {
                    switch (result) {
                        case SetPresenceResult.Updated ignored ->
                                sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.PRESENCE_UPDATE_SUCCESS, Placeholder.unparsed("presence", presenceState.name().toLowerCase())));
                    }
                }).exceptionally(throwable -> {
                    LOGGER.error("Failed to set presence for player {}", sender.getUniqueId(), throwable);
                    sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}
