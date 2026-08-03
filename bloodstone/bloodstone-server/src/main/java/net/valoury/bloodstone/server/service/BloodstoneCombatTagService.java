package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class BloodstoneCombatTagService {

    private static final long COMBAT_DURATION_NANOSECONDS =
            TimeUnit.SECONDS.toNanos(15);

    private final Map<UUID, CombatTag> combatTags = new HashMap<>();
    private final Map<UUID, ExperienceSnapshot> pendingExperienceRestores =
            new HashMap<>();

    public boolean isTagged(UUID playerId) {
        return combatTags.containsKey(playerId);
    }

    public void tag(Player player) {
        tag(player, System.nanoTime());
    }

    public void remove(Player player, boolean notify) {
        CombatTag combatTag = combatTags.remove(player.getUniqueId());
        if (combatTag == null) {
            return;
        }
        restoreExperience(player, combatTag.experienceSnapshot());
        if (notify && player.isOnline()) {
            BloodstoneText.sendActionBar(
                    player,
                    BloodstoneServerConstants.COMBAT_EXIT_ACTION_BAR
            );
        }
    }

    public void preserveExperienceOnDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        CombatTag combatTag = combatTags.get(player.getUniqueId());
        ExperienceSnapshot experienceSnapshot = combatTag == null
                ? new ExperienceSnapshot(
                        player.getLevel(),
                        player.getExp(),
                        player.getTotalExperience()
                )
                : combatTag.experienceSnapshot();
        pendingExperienceRestores.put(
                player.getUniqueId(),
                experienceSnapshot
        );
        event.setDroppedExp(0);
        event.setKeepLevel(true);
        restoreExperience(player, experienceSnapshot);
    }

    public void restorePendingExperience(Player player) {
        ExperienceSnapshot experienceSnapshot =
                pendingExperienceRestores.remove(player.getUniqueId());
        if (experienceSnapshot != null) {
            restoreExperience(player, experienceSnapshot);
        }
    }

    public void tick() {
        long nowNanoseconds = System.nanoTime();
        List<UUID> expiredTags = new ArrayList<>();
        for (Map.Entry<UUID, CombatTag> entry : combatTags.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            int seconds = remainingCombatSeconds(
                    entry.getValue().expiresAtNanoseconds(),
                    nowNanoseconds
            );
            if (seconds == 0) {
                expiredTags.add(entry.getKey());
                continue;
            }
            player.setLevel(seconds);
            player.setExp(combatProgress(seconds));
        }
        for (UUID playerId : expiredTags) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                remove(player, true);
            } else {
                combatTags.remove(playerId);
            }
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, CombatTag> entry
                : new ArrayList<>(combatTags.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                restoreExperience(
                        player,
                        entry.getValue().experienceSnapshot()
                );
            }
        }
        combatTags.clear();
        pendingExperienceRestores.clear();
    }

    @Contract(pure = true)
    static int remainingCombatSeconds(
            long expiresAtNanoseconds,
            long nowNanoseconds
    ) {
        long remainingNanoseconds = expiresAtNanoseconds - nowNanoseconds;
        if (remainingNanoseconds <= 0) {
            return 0;
        }
        return Math.toIntExact(
                Math.floorDiv(
                        remainingNanoseconds - 1,
                        1_000_000_000L
                ) + 1
        );
    }

    @Contract(pure = true)
    static float combatProgress(int secondsRemaining) {
        float elapsedFraction = Math.max(
                0,
                Math.min(15, secondsRemaining)
        ) / 15.0F;
        return Math.min(0.99F, elapsedFraction * 0.99F);
    }

    private void tag(Player player, long nowNanoseconds) {
        CombatTag existing = combatTags.get(player.getUniqueId());
        if (existing == null) {
            combatTags.put(
                    player.getUniqueId(),
                    new CombatTag(
                            player.getLevel(),
                            player.getExp(),
                            player.getTotalExperience(),
                            nowNanoseconds + COMBAT_DURATION_NANOSECONDS
                    )
            );
            BloodstoneText.sendActionBar(
                    player,
                    BloodstoneServerConstants.COMBAT_ENTER_ACTION_BAR
            );
        } else {
            combatTags.put(
                    player.getUniqueId(),
                    new CombatTag(
                            existing.originalLevel(),
                            existing.originalProgress(),
                            existing.originalTotalExperience(),
                            nowNanoseconds + COMBAT_DURATION_NANOSECONDS
                    )
            );
        }
        player.setLevel(15);
        player.setExp(0.99F);
    }

    private static void restoreExperience(
            Player player,
            ExperienceSnapshot experienceSnapshot
    ) {
        player.setTotalExperience(experienceSnapshot.totalExperience());
        player.setLevel(experienceSnapshot.level());
        player.setExp(experienceSnapshot.progress());
    }

    private record CombatTag(
            int originalLevel,
            float originalProgress,
            int originalTotalExperience,
            long expiresAtNanoseconds
    ) {
        private ExperienceSnapshot experienceSnapshot() {
            return new ExperienceSnapshot(
                    originalLevel,
                    originalProgress,
                    originalTotalExperience
            );
        }
    }

    private record ExperienceSnapshot(
            int level,
            float progress,
            int totalExperience
    ) {
    }
}
