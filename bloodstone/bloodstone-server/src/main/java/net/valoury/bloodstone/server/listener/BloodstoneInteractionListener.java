package net.valoury.bloodstone.server.listener;

import net.valoury.bloodstone.server.service.BloodstoneMachineService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

public final class BloodstoneInteractionListener implements Listener {

    private final BloodstoneMachineService machineService;

    public BloodstoneInteractionListener(BloodstoneMachineService machineService) {
        this.machineService = machineService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        machineService.handleBlockInteraction(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        machineService.handleItemFrame(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        machineService.handleResistancePotion(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickup(PlayerPickupItemEvent event) {
        machineService.handleBloodPickup(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupCompletion(PlayerPickupItemEvent event) {
        machineService.handleBloodPickupCompletion(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        machineService.handleBloodMerge(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemMergeCompletion(ItemMergeEvent event) {
        machineService.handleBloodMergeCompletion(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        machineService.handleItemDespawn(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        machineService.handlePossibleBloodDropRemoval(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        machineService.handleDisposableItemSpawn(event);
    }
}
