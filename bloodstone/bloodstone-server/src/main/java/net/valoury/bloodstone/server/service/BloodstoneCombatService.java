package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BloodstoneCombatService {

    private static final double BLOOD_DROP_CHANCE = 0.5D;
    private static final long COMBAT_DURATION_MILLISECONDS = 15_000L;

    private final BloodstoneCurrencyService currencyService;
    private final BloodstoneCombatTagService combatTagService;
    private final BloodstoneEffectAxeCombatService effectAxeCombatService;
    private final BloodstoneCombatResolutionService combatResolutionService;
    private final BloodstonePresentationService presentationService;
    private final BloodstonePlayerService playerService;
    private final BloodstonePlayerNameService playerNameService;
    private final CombatAttributionTracker attributionTracker =
            new CombatAttributionTracker();

    public BloodstoneCombatService(
            BloodstoneCurrencyService currencyService,
            BloodstoneCombatTagService combatTagService,
            BloodstoneEffectAxeCombatService effectAxeCombatService,
            BloodstoneCombatResolutionService combatResolutionService,
            BloodstonePresentationService presentationService,
            BloodstonePlayerService playerService,
            BloodstonePlayerNameService playerNameService
    ) {
        this.currencyService = currencyService;
        this.combatTagService = combatTagService;
        this.effectAxeCombatService = effectAxeCombatService;
        this.combatResolutionService = combatResolutionService;
        this.presentationService = presentationService;
        this.playerService = playerService;
        this.playerNameService = playerNameService;
    }

    public boolean isTagged(UUID playerId) {
        return combatTagService.isTagged(playerId);
    }

    public boolean isDominatedBy(
            UUID dominatedPlayerId,
            UUID potentialDominatorId
    ) {
        return combatResolutionService.isDominatedBy(
                dominatedPlayerId,
                potentialDominatorId
        );
    }

    public void forceKillAttribution(
            UUID victimId,
            UUID killerId,
            double attributionDamage
    ) {
        attributionTracker.forceKiller(
                victimId,
                killerId,
                attributionDamage
        );
    }

    public void handleDuelEnd(Player player) {
        combatTagService.remove(player, true);
        attributionTracker.discard(player.getUniqueId());
    }

    public void handleDamage(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player player
                ? player
                : null;
        Player attacker = resolveAttacker(event.getDamager());
        if (victim != null
                && attacker != null
                && isInBloodstone(victim)
                && shouldCancelSelfInflictedBowDamage(
                        event.getDamager() instanceof Arrow,
                        victim.getUniqueId(),
                        attacker.getUniqueId()
                )) {
            event.setCancelled(true);
            return;
        }
        if (victim == null
                || attacker == null
                || attacker.equals(victim)
                || !isInBloodstone(victim)
                || !isInBloodstone(attacker)) {
            return;
        }
        if (attributionTracker.isForcedKiller(
                victim.getUniqueId(),
                attacker.getUniqueId()
        )) {
            return;
        }
        if (!playerService.isLoaded(victim.getUniqueId())
                || !playerService.isLoaded(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Thorns reports the original melee roles in reverse.
        if (BloodstoneEffectAxeCombatService
                .shouldActivateFromDamageEvent(event.getCause())) {
            handleEffectAxeAttack(
                    victim,
                    attacker,
                    victim.getInventory().getHeldItemSlot()
            );
        }

        attributionTracker.record(
                victim.getUniqueId(),
                attacker.getUniqueId(),
                event.getFinalDamage(),
                System.currentTimeMillis()
        );
        combatTagService.tag(attacker);
        combatTagService.tag(victim);

        if (event.getDamager() instanceof Arrow arrow
                && arrow.isCritical()) {
            dropBlood(attacker, victim);
            sendArrowFeedback(
                    attacker,
                    victim,
                    arrow,
                    event.getFinalDamage()
            );
        } else if (event.getDamager() instanceof Player) {
            ItemStack heldItem = attacker.getItemInHand();
            if (heldItem != null
                    && (heldItem.getType() == Material.DIAMOND_SWORD
                    || heldItem.getType() == Material.DIAMOND_AXE)) {
                dropBlood(attacker, victim);
            }
        }
    }

    public void handleDeath(Player victim) {
        combatTagService.remove(victim, false);
        combatResolutionService.handleDeath(
                victim,
                attributionTracker.take(
                        victim.getUniqueId(),
                        System.currentTimeMillis()
                )
        );
    }

    public void preserveExperienceOnDeath(PlayerDeathEvent event) {
        combatTagService.preserveExperienceOnDeath(event);
    }

    public void handleQuit(Player player) {
        if (isTagged(player.getUniqueId())) {
            player.setHealth(0.0D);
        }
        combatResolutionService.handleQuit(player.getUniqueId());
        effectAxeCombatService.handleQuit(player.getUniqueId());
    }

    public void handleRespawn(Player player) {
        combatTagService.restorePendingExperience(player);
        combatResolutionService.handleRespawn(player);
    }

    public void handleEnteredSpawn(Player player) {
        combatTagService.remove(player, true);
    }

    public void tick() {
        combatTagService.tick();
        attributionTracker.expire(
                System.currentTimeMillis(),
                COMBAT_DURATION_MILLISECONDS
        );
        effectAxeCombatService.tick();
        combatResolutionService.tick();
    }

    public void shutdown() {
        combatTagService.shutdown();
        attributionTracker.clear();
        effectAxeCombatService.clear();
        combatResolutionService.clear();
    }

    public void handleEffectAxeAttack(
            Player attacker,
            Player victim,
            int heldSlot
    ) {
        effectAxeCombatService.handleAttack(attacker, victim, heldSlot);
    }

    @Contract(pure = true)
    static boolean shouldCancelSelfInflictedBowDamage(
            boolean causedByArrow,
            @Nullable UUID damagedPlayerId,
            @Nullable UUID shooterId
    ) {
        return causedByArrow
                && damagedPlayerId != null
                && damagedPlayerId.equals(shooterId);
    }

    @Contract(pure = true)
    static boolean isBloodDropRoll(double randomValue) {
        if (!Double.isFinite(randomValue)
                || randomValue < 0.0D
                || randomValue >= 1.0D) {
            throw new IllegalArgumentException(
                    "Random value must be in the range [0.0, 1.0)"
            );
        }
        return randomValue < BLOOD_DROP_CHANCE;
    }

    @Contract(pure = true)
    static boolean isHeadshot(double arrowImpactY, double victimEyeY) {
        return arrowImpactY >= victimEyeY;
    }

    private void dropBlood(Player attacker, Player victim) {
        if (!isBloodDropRoll(ThreadLocalRandom.current().nextDouble())) {
            return;
        }
        int amount = BloodstoneRank.resolve(attacker)
                .bloodPerQualifyingHit();
        Item dropped = victim.getWorld().dropItem(
                victim.getLocation().clone().add(0.0D, 1.0D, 0.0D),
                currencyService.createBlood(amount)
        );
        dropped.setVelocity(dropped.getVelocity().setY(0.16D));
        presentationService.playBloodDropStep(victim.getLocation());
    }

    private void sendArrowFeedback(
            Player attacker,
            Player victim,
            Arrow arrow,
            double finalDamage
    ) {
        if (!isHeadshot(
                arrow.getLocation().getY(),
                victim.getEyeLocation().getY()
        )) {
            return;
        }
        double remainingHealth = Math.max(
                0.0D,
                victim.getHealth() - finalDamage
        );
        BloodstoneText.sendMessage(
                attacker,
                BloodstoneServerConstants.ARROW_FEEDBACK_FORMAT,
                Placeholder.component(
                        "shot",
                        BloodstoneText.deserialize(
                                BloodstoneServerConstants.HEADSHOT_DISPLAY
                        )
                ),
                Placeholder.component(
                        "victim",
                        playerNameService.resolveOnlinePlayerName(victim)
                ),
                Placeholder.unparsed(
                        "health",
                        formatHealth(remainingHealth)
                )
        );
    }

    private static @Nullable Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return null;
    }

    private static boolean isInBloodstone(Player player) {
        return BloodstoneServerConstants.WORLD_NAME.equals(
                player.getWorld().getName()
        );
    }

    private static String formatHealth(double health) {
        return String.format(Locale.US, "%.1f", health);
    }
}
