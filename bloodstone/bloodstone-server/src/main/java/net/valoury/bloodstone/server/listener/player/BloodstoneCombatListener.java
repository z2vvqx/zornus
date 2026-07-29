package net.valoury.bloodstone.server.listener.player;

import net.valoury.bloodstone.server.service.BloodstoneCombatService;
import net.valoury.bloodstone.server.service.BloodstoneItemService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.jspecify.annotations.NonNull;

public final class BloodstoneCombatListener implements Listener {

    private final BloodstoneCombatService combatService;
    private final BloodstoneItemService itemService;

    public BloodstoneCombatListener(
            BloodstoneCombatService combatService,
            BloodstoneItemService itemService
    ) {
        this.combatService = combatService;
        this.itemService = itemService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        combatService.handleDamage(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(@NonNull PlayerItemDamageEvent event) {
        if (itemService.isEffectAxe(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
