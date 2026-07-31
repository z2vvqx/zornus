package net.valoury.bloodstone.server.listener.player;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.service.*;
import net.valoury.bloodstone.server.registrar.BloodstoneEffectAxePacketRegistrar;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NonNull;

public final class BloodstoneConnectionListener implements Listener {

    private static final int JOIN_MESSAGE_SPACER_LINE_COUNT = 20;

    private final BloodstonePlayerService playerService;
    private final BloodstoneCombatService combatService;
    private final BloodstoneDuelService duelService;
    private final BloodstoneStorageService storageService;
    private final BloodstoneEnchanterService enchanterService;
    private final BloodstoneAxeFuserService axeFuserService;
    private final BloodstoneGuildProfileCache guildProfileCache;
    private final BloodstoneEffectAxePacketRegistrar effectAxePacketRegistrar;
    private final BloodstoneMessageService messageService;

    public BloodstoneConnectionListener(
            BloodstonePlayerService playerService,
            BloodstoneCombatService combatService,
            BloodstoneDuelService duelService,
            BloodstoneStorageService storageService,
            BloodstoneEnchanterService enchanterService,
            BloodstoneAxeFuserService axeFuserService,
            BloodstoneGuildProfileCache guildProfileCache,
            BloodstoneEffectAxePacketRegistrar effectAxePacketRegistrar,
            BloodstoneMessageService messageService
    ) {
        this.playerService = playerService;
        this.combatService = combatService;
        this.duelService = duelService;
        this.storageService = storageService;
        this.enchanterService = enchanterService;
        this.axeFuserService = axeFuserService;
        this.guildProfileCache = guildProfileCache;
        this.effectAxePacketRegistrar = effectAxePacketRegistrar;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void sendJoinMessageSpacer(@NonNull PlayerJoinEvent event) {
        for (int line = 0; line < JOIN_MESSAGE_SPACER_LINE_COUNT; line++) {
            event.getPlayer().sendMessage(Component.empty());
        }
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
        axeFuserService.handleDisconnect(event.getPlayer().getUniqueId());
        effectAxePacketRegistrar.handleDisconnect(event.getPlayer().getUniqueId());
        guildProfileCache.remove(event.getPlayer().getUniqueId());
        messageService.clear(event.getPlayer().getUniqueId());
        storageService.handleQuit(event.getPlayer());
        playerService.handleQuit(event.getPlayer());
    }
}
