package net.valoury.punishments.proxy.listener.player;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.model.PunishmentType;
import net.valoury.punishments.proxy.service.PunishmentService;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public final class PunishmentChatListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentChatListener.class);

    private final PunishmentService punishmentService;

    public PunishmentChatListener(@NonNull PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    @Subscribe
    public EventTask onPlayerChat(@NonNull PlayerChatEvent event) {
        Player player = event.getPlayer();
        CompletableFuture<Void> decision = punishmentService
                .fetchActive(player.getUniqueId(), PunishmentType.MUTE)
                .thenAccept(punishment -> {
                    if (punishment.isPresent()) {
                        event.setResult(PlayerChatEvent.ChatResult.denied());
                        player.sendMessage(StringUtils.deserialize(PunishmentProxyConstants.ENFORCEMENT_MUTED));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to verify mute status for {} ({})",
                            player.getUsername(), player.getUniqueId(), throwable);
                    event.setResult(PlayerChatEvent.ChatResult.denied());
                    player.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
        return EventTask.resumeWhenComplete(decision);
    }
}
