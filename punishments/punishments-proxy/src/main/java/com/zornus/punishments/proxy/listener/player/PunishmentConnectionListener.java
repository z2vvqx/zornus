package com.zornus.punishments.proxy.listener.player;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.zornus.punishments.proxy.PunishmentProxyConstants;
import com.zornus.punishments.proxy.model.PunishmentType;
import com.zornus.punishments.proxy.service.PunishmentService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public final class PunishmentConnectionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentConnectionListener.class);

    private final PunishmentService punishmentService;

    public PunishmentConnectionListener(@NonNull PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    @Subscribe
    public EventTask onLogin(@NonNull LoginEvent event) {
        Player player = event.getPlayer();
        CompletableFuture<Void> decision = punishmentService
                .fetchActive(player.getUniqueId(), PunishmentType.BAN)
                .thenAccept(punishment -> {
                    if (punishment.isPresent()) {
                        event.setResult(ResultedEvent.ComponentResult.denied(
                                StringUtils.deserialize(PunishmentProxyConstants.ENFORCEMENT_BANNED)));
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to verify ban status for {} ({})",
                            player.getUsername(), player.getUniqueId(), throwable);
                    event.setResult(ResultedEvent.ComponentResult.denied(
                            StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED)));
                    return null;
                });
        return EventTask.resumeWhenComplete(decision);
    }

    @Subscribe
    public EventTask onPostLogin(@NonNull PostLoginEvent event) {
        Player player = event.getPlayer();
        CompletableFuture<Void> notifications =
                punishmentService.handlePlayerJoin(player).exceptionally(throwable -> {
                    LOGGER.error("Failed to handle punishment login for {} ({})",
                            player.getUsername(), player.getUniqueId(), throwable);
                    return null;
                });
        return EventTask.resumeWhenComplete(notifications);
    }
}
