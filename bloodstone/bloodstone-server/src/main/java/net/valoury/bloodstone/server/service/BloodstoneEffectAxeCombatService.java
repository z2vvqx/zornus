package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectAxeDefinition;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectTarget;
import net.valoury.bloodstone.server.EffectAxeItemDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class BloodstoneEffectAxeCombatService {

    private static final long COOLDOWN_NANOSECONDS =
            TimeUnit.SECONDS.toNanos(1);

    private final BloodstoneEffectAxeService effectAxeService;
    private final BloodstoneSpawnProtectionService spawnProtectionService;
    private final BloodstonePresentationService presentationService;
    private final BloodstonePlayerService playerService;
    private final Map<EffectAxeTargetCooldown, Long> cooldowns =
            new HashMap<>();

    public BloodstoneEffectAxeCombatService(
            BloodstoneEffectAxeService effectAxeService,
            BloodstoneSpawnProtectionService spawnProtectionService,
            BloodstonePresentationService presentationService,
            BloodstonePlayerService playerService
    ) {
        this.effectAxeService = effectAxeService;
        this.spawnProtectionService = spawnProtectionService;
        this.presentationService = presentationService;
        this.playerService = playerService;
    }

    public void handleAttack(
            Player attacker,
            Player victim,
            int heldSlot
    ) {
        if (heldSlot < 0
                || heldSlot > 8
                || !isInBloodstone(attacker)
                || !isInBloodstone(victim)
                || !playerService.isLoaded(attacker.getUniqueId())
                || !playerService.isLoaded(victim.getUniqueId())) {
            return;
        }
        ItemStack heldItem = attacker.getInventory().getItem(heldSlot);
        if (heldItem == null
                || spawnProtectionService.isInsideSpawn(attacker)
                || spawnProtectionService.isInsideSpawn(victim)) {
            return;
        }
        Optional<EffectAxeItemDefinition> definitionOptional =
                effectAxeService.definition(heldItem);
        if (definitionOptional.isEmpty()) {
            return;
        }
        EffectAxeItemDefinition definition = definitionOptional.get();
        long nowNanoseconds = System.nanoTime();
        EffectAxeTargetCooldown cooldown = new EffectAxeTargetCooldown(
                attacker.getUniqueId(),
                victim.getUniqueId(),
                definition.id()
        );
        Long previousUse = cooldowns.get(cooldown);
        if (!isActivationReady(previousUse, nowNanoseconds)) {
            return;
        }
        cooldowns.put(cooldown, nowNanoseconds);

        int particleCount = BloodstonePresentationService
                .effectAxeParticleCount(definition.effects().size());
        for (EffectAxeDefinition effect : definition.effects()) {
            Player effectRecipient = effect.target() == EffectTarget.SELF
                    ? attacker
                    : victim;
            effectRecipient.addPotionEffect(
                    effect.createPotionEffect(),
                    true
            );
            presentationService.playEffectAxeParticles(
                    effectRecipient,
                    effect.particleColor(),
                    particleCount
            );
        }
        presentationService.playEffectAxeSound();
        if (effectAxeService.consumeUse(heldItem)) {
            attacker.getInventory().clear(heldSlot);
            presentationService.playEffectAxeBreak(attacker);
        } else {
            attacker.getInventory().setItem(heldSlot, heldItem);
        }
    }

    public void handleQuit(UUID playerId) {
        cooldowns.keySet().removeIf(key ->
                key.attackerId().equals(playerId)
                        || key.victimId().equals(playerId));
    }

    public void tick() {
        long nowNanoseconds = System.nanoTime();
        cooldowns.entrySet().removeIf(entry ->
                nowNanoseconds - entry.getValue() >= COOLDOWN_NANOSECONDS);
    }

    public void clear() {
        cooldowns.clear();
    }

    @Contract(pure = true)
    static boolean isActivationReady(
            @Nullable Long previousUseNanoseconds,
            long nowNanoseconds
    ) {
        return previousUseNanoseconds == null
                || nowNanoseconds - previousUseNanoseconds
                >= COOLDOWN_NANOSECONDS;
    }

    @Contract(pure = true)
    static boolean shouldActivateFromDamageEvent(
            @Nullable DamageCause damageCause
    ) {
        return damageCause == DamageCause.THORNS;
    }

    private static boolean isInBloodstone(Player player) {
        return BloodstoneServerConstants.WORLD_NAME.equals(
                player.getWorld().getName()
        );
    }

    private record EffectAxeTargetCooldown(
            UUID attackerId,
            UUID victimId,
            String axeId
    ) {
    }
}
