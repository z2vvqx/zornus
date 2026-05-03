package com.zornus.guilds.proxy.registrar;

import com.velocitypowered.api.event.EventManager;
import com.zornus.guilds.proxy.listener.player.GuildConnectionListener;
import com.zornus.guilds.proxy.service.GuildService;
import org.jspecify.annotations.NonNull;

public final class GuildListenerRegistrar {

    private final @NonNull Object plugin;
    private final @NonNull GuildService guildService;

    public GuildListenerRegistrar(@NonNull Object plugin, @NonNull GuildService guildService) {
        this.plugin = plugin;
        this.guildService = guildService;
    }

    public void registerListeners(@NonNull EventManager eventManager) {
        eventManager.register(plugin, new GuildConnectionListener(guildService));
    }
}
