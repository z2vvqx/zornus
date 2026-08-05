package net.valoury.bloodstone.server.listener.player;

import net.valoury.bloodstone.server.service.BloodstoneCombatService;
import net.valoury.bloodstone.server.service.BloodstoneDuelService;
import net.valoury.bloodstone.server.service.BloodstoneService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

public final class BloodstoneDeathAndRespawnListener implements Listener {

    private static final long FORCED_RESPAWN_DELAY_TICKS = 5L;

    private final BloodstoneService bloodstoneService;
    private final BloodstoneCombatService combatService;
    private final BloodstoneDuelService duelService;
    private final Plugin plugin;

    public BloodstoneDeathAndRespawnListener(
            BloodstoneService bloodstoneService,
            BloodstoneCombatService combatService,
            BloodstoneDuelService duelService,
            Plugin plugin
    ) {
        this.bloodstoneService = bloodstoneService;
        this.combatService = combatService;
        this.duelService = duelService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(@NonNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!bloodstoneService.isInBloodstoneWorld(player)) {
            return;
        }

        combatService.preserveExperienceOnDeath(event);
        bloodstoneService.handlePlayerDeath(player, event.getDrops());
        duelService.handleDeath(player);
        combatService.handleDeath(player);
        scheduleForcedRespawn(player);
    }

    @EventHandler
    public void onPlayerRespawn(@NonNull PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!bloodstoneService.isInBloodstoneWorld(player)) {
            return;
        }

        event.setRespawnLocation(bloodstoneService.selectRespawnLocation(player.getWorld()));
        bloodstoneService.restoreBaselineKit(player);
        player.updateInventory();
        bloodstoneService.playBaselineRestoredFeedback(player);
        combatService.handleRespawn(player);
    }

    private void scheduleForcedRespawn(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()
                    || !player.isDead()
                    || !bloodstoneService.isInBloodstoneWorld(player)) {
                return;
            }
            player.spigot().respawn();
        }, FORCED_RESPAWN_DELAY_TICKS);
    }
}
