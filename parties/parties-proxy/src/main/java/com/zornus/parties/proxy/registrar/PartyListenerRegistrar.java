package com.zornus.parties.proxy.registrar;

import com.velocitypowered.api.event.EventManager;
import com.zornus.parties.proxy.service.PartyService;
import org.jspecify.annotations.NonNull;

public final class PartyListenerRegistrar {

    private final @NonNull Object plugin;
    private final @NonNull PartyService partyService;

    public PartyListenerRegistrar(@NonNull Object plugin, @NonNull PartyService partyService) {
        this.plugin = plugin;
        this.partyService = partyService;
    }

    public void registerListeners(@NonNull EventManager eventManager) {
    }
}
