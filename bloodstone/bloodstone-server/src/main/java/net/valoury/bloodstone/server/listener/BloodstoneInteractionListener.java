package net.valoury.bloodstone.server.listener;

import net.valoury.bloodstone.server.service.BloodstoneMachineService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
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
    public void onPlayerInteractEntity(PlayerInteractAtEntityEvent event) {
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

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        machineService.handleDisposableItemSpawn(event);
    }
}
