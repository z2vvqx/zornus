package net.valoury.bloodstone.server.listener.player;

import net.valoury.bloodstone.server.service.BloodstoneCombatService;
import net.valoury.bloodstone.server.service.BloodstoneEffectAxeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.jspecify.annotations.NonNull;

public final class BloodstoneCombatListener implements Listener {

    private final BloodstoneCombatService combatService;
    private final BloodstoneEffectAxeService effectAxeService;

    public BloodstoneCombatListener(
            BloodstoneCombatService combatService,
            BloodstoneEffectAxeService effectAxeService
    ) {
        this.combatService = combatService;
        this.effectAxeService = effectAxeService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        combatService.handleDamage(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(@NonNull PlayerItemDamageEvent event) {
        if (effectAxeService.isEffectAxe(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
