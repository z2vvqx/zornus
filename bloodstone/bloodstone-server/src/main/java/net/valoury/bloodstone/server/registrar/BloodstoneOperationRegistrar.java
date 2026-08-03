package net.valoury.bloodstone.server.registrar;

import net.valoury.bloodstone.server.service.BloodstoneCombatService;
import net.valoury.bloodstone.server.service.BloodstoneGuildProfileCache;
import net.valoury.bloodstone.server.service.BloodstoneLeaderboardService;
import net.valoury.bloodstone.server.service.BloodstoneMainThreadExecutor;
import net.valoury.bloodstone.server.service.BloodstoneStorageService;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class BloodstoneOperationRegistrar {

    private static final long COMBAT_TICK_INTERVAL = 20L;
    private static final long STORAGE_CHECKPOINT_INTERVAL = 200L;
    private static final long GUILD_PROFILE_REFRESH_INTERVAL = 100L;
    private static final long LEADERBOARD_RETRY_DELAY = 200L;

    private final Plugin plugin;
    private final BloodstoneCombatService combatService;
    private final BloodstoneStorageService storageService;
    private final BloodstoneGuildProfileCache guildProfileCache;
    private final BloodstoneLeaderboardService leaderboardService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final long leaderboardRefreshTicks;
    private final AtomicBoolean guildProfileRefreshRunning = new AtomicBoolean();
    private final AtomicBoolean leaderboardRefreshRunning = new AtomicBoolean();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private @Nullable BukkitTask leaderboardRetryTask;

    public BloodstoneOperationRegistrar(
            Plugin plugin,
            BloodstoneCombatService combatService,
            BloodstoneStorageService storageService,
            BloodstoneGuildProfileCache guildProfileCache,
            BloodstoneLeaderboardService leaderboardService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            long leaderboardRefreshSeconds
    ) {
        this.plugin = plugin;
        this.combatService = combatService;
        this.storageService = storageService;
        this.guildProfileCache = guildProfileCache;
        this.leaderboardService = leaderboardService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.leaderboardRefreshTicks = Math.max(20L, leaderboardRefreshSeconds * 20L);
    }

    public void registerOperations() {
        shuttingDown.set(false);
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                combatService::tick,
                COMBAT_TICK_INTERVAL,
                COMBAT_TICK_INTERVAL
        ));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                storageService::checkpointActiveStorages,
                STORAGE_CHECKPOINT_INTERVAL,
                STORAGE_CHECKPOINT_INTERVAL
        ));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::refreshGuildProfiles,
                GUILD_PROFILE_REFRESH_INTERVAL,
                GUILD_PROFILE_REFRESH_INTERVAL
        ));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::refreshLeaderboards,
                leaderboardRefreshTicks,
                leaderboardRefreshTicks
        ));
    }

    public void cancelOperations() {
        shuttingDown.set(true);
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        cancelLeaderboardRetry();
    }

    private void refreshGuildProfiles() {
        if (shuttingDown.get()
                || !guildProfileRefreshRunning.compareAndSet(false, true)) {
            return;
        }
        guildProfileCache.refreshAll(
                plugin.getServer().getOnlinePlayers().stream()
                        .map(player -> player.getUniqueId())
                        .toList()
        ).whenComplete((ignored, exception) ->
                guildProfileRefreshRunning.set(false));
    }

    private void refreshLeaderboards() {
        if (shuttingDown.get() || !leaderboardRefreshRunning.compareAndSet(false, true)) {
            return;
        }
        leaderboardService.refresh().whenComplete((snapshot, exception) -> {
            if (shuttingDown.get()) {
                leaderboardRefreshRunning.set(false);
                return;
            }
            mainThreadExecutor.execute(() -> {
                    leaderboardRefreshRunning.set(false);
                    if (exception == null) {
                        cancelLeaderboardRetry();
                        return;
                    }
                    if (shuttingDown.get()) {
                        return;
                    }
                    plugin.getLogger().log(Level.WARNING,
                            "Bloodstone leaderboard refresh failed; a later retry was scheduled", exception);
                    scheduleLeaderboardRetry();
                });
        });
    }

    private void scheduleLeaderboardRetry() {
        if (leaderboardRetryTask != null
                && !leaderboardRetryTask.isCancelled()) {
            return;
        }
        leaderboardRetryTask = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    leaderboardRetryTask = null;
                    refreshLeaderboards();
                },
                LEADERBOARD_RETRY_DELAY
        );
    }

    private void cancelLeaderboardRetry() {
        if (leaderboardRetryTask == null) {
            return;
        }
        leaderboardRetryTask.cancel();
        leaderboardRetryTask = null;
    }
}
