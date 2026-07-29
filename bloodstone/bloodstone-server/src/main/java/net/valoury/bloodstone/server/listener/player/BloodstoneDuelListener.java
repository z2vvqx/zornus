package net.valoury.bloodstone.server.listener.player;

import net.valoury.bloodstone.server.service.BloodstoneDuelService;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jspecify.annotations.Nullable;

public final class BloodstoneDuelListener implements Listener {

    private final BloodstoneDuelService duelService;

    public BloodstoneDuelListener(BloodstoneDuelService duelService) {
        this.duelService = duelService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (duelService.shouldCancelDamage(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!duelService.isCountingDown(event.getPlayer().getUniqueId())) {
            return;
        }

        Location origin = event.getFrom();
        @Nullable Location destination = event.getTo();
        if (destination == null
                || (origin.getX() == destination.getX()
                && origin.getY() == destination.getY()
                && origin.getZ() == destination.getZ())) {
            return;
        }

        Location lockedDestination = origin.clone();
        lockedDestination.setYaw(destination.getYaw());
        lockedDestination.setPitch(destination.getPitch());
        event.setTo(lockedDestination);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (duelService.isDueling(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
