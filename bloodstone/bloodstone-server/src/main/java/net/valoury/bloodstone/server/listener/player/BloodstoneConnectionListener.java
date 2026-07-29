package net.valoury.bloodstone.server.listener.player;

import net.valoury.bloodstone.server.service.*;
import net.valoury.bloodstone.server.registrar.BloodstoneEffectAxePacketRegistrar;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NonNull;

public final class BloodstoneConnectionListener implements Listener {

    private final BloodstonePlayerService playerService;
    private final BloodstoneCombatService combatService;
    private final BloodstoneDuelService duelService;
    private final BloodstoneStorageService storageService;
    private final BloodstoneEnchanterService enchanterService;
    private final BloodstoneGuildProfileCache guildProfileCache;
    private final BloodstoneEffectAxePacketRegistrar effectAxePacketRegistrar;
    private final BloodstoneMessageService messageService;

    public BloodstoneConnectionListener(
            BloodstonePlayerService playerService,
            BloodstoneCombatService combatService,
            BloodstoneDuelService duelService,
            BloodstoneStorageService storageService,
            BloodstoneEnchanterService enchanterService,
            BloodstoneGuildProfileCache guildProfileCache,
            BloodstoneEffectAxePacketRegistrar effectAxePacketRegistrar,
            BloodstoneMessageService messageService
    ) {
        this.playerService = playerService;
        this.combatService = combatService;
        this.duelService = duelService;
        this.storageService = storageService;
        this.enchanterService = enchanterService;
        this.guildProfileCache = guildProfileCache;
        this.effectAxePacketRegistrar = effectAxePacketRegistrar;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NonNull PlayerJoinEvent event) {
        playerService.handleJoin(event.getPlayer());
        guildProfileCache.refresh(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(@NonNull PlayerQuitEvent event) {
        duelService.handleQuit(event.getPlayer());
        combatService.handleQuit(event.getPlayer());
        enchanterService.handleDisconnect(event.getPlayer().getUniqueId());
        effectAxePacketRegistrar.handleDisconnect(event.getPlayer().getUniqueId());
        guildProfileCache.remove(event.getPlayer().getUniqueId());
        messageService.clear(event.getPlayer().getUniqueId());
        storageService.handleQuit(event.getPlayer());
        playerService.handleQuit(event.getPlayer());
    }
}
