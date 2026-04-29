package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.PresenceState;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.NonNull;

/**
 * Command for managing online/offline presence state visibility.
 */
public final class FriendPresenceCommand {

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
        Player sender = (Player) context.getSource();

        // Placeholder: get current presence from service
        // String currentPresence = friendService.getPlayerPresence(sender.getUniqueId()).join();
        String currentPresence = "online";

        sender.sendMessage(StringUtils.deserialize(
                FriendProxyConstants.PRESENCE_DISPLAY,
                Placeholder.unparsed("presence", currentPresence)
        ));

        return Command.SINGLE_SUCCESS;
    }

    private static int handleUpdatePresence(@NonNull CommandContext<CommandSource> context, @NonNull FriendService friendService, PresenceState presenceState) {
        Player sender = (Player) context.getSource();

        friendService.setPresence(sender.getUniqueId(), presenceState).thenAccept(result -> {
            switch (result) {
                case STATUS_UPDATED ->
                        sender.sendMessage(StringUtils.deserialize(FriendProxyConstants.PRESENCE_UPDATE_SUCCESS, Placeholder.unparsed("presence", presenceState.name().toLowerCase())));
                default -> sender.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
