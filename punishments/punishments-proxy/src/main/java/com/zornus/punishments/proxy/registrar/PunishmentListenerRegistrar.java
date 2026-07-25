package com.zornus.punishments.proxy.registrar;

import com.velocitypowered.api.event.EventManager;
import com.zornus.punishments.proxy.listener.player.PunishmentChatListener;
import com.zornus.punishments.proxy.listener.player.PunishmentConnectionListener;
import com.zornus.punishments.proxy.service.PunishmentService;
import org.jspecify.annotations.NonNull;

public final class PunishmentListenerRegistrar {
    private final @NonNull Object plugin;
    private final @NonNull PunishmentService punishmentService;

    public PunishmentListenerRegistrar(
            @NonNull Object plugin,
            @NonNull PunishmentService punishmentService
    ) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
    }

    public void registerListeners(@NonNull EventManager eventManager) {
        eventManager.register(plugin, new PunishmentConnectionListener(punishmentService));
        eventManager.register(plugin, new PunishmentChatListener(punishmentService));
    }
}
