package net.valoury.bloodstone.server.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class CombatAttributionTracker {

    private final Map<UUID, Map<UUID, DamageContribution>>
            contributionsByVictim = new HashMap<>();
    private final Map<UUID, UUID> forcedKillerByVictim = new HashMap<>();

    void forceKiller(
            UUID victimId,
            UUID killerId,
            double attributionDamage
    ) {
        if (!Double.isFinite(attributionDamage)
                || attributionDamage <= 0.0D) {
            throw new IllegalArgumentException(
                    "Attribution damage must be positive and finite"
            );
        }
        forcedKillerByVictim.put(victimId, killerId);
        Map<UUID, DamageContribution> forcedContribution = new HashMap<>();
        forcedContribution.put(
                killerId,
                new DamageContribution(
                        attributionDamage,
                        System.currentTimeMillis()
                )
        );
        contributionsByVictim.put(victimId, forcedContribution);
    }

    boolean isForcedKiller(UUID victimId, UUID attackerId) {
        return attackerId.equals(forcedKillerByVictim.get(victimId));
    }

    void record(
            UUID victimId,
            UUID attackerId,
            double damage,
            long recordedAt
    ) {
        Map<UUID, DamageContribution> contributions =
                contributionsByVictim.computeIfAbsent(
                        victimId,
                        ignored -> new HashMap<>()
                );
        DamageContribution previous = contributions.get(attackerId);
        double accumulatedDamage = previous == null
                ? damage
                : previous.damage() + damage;
        contributions.put(
                attackerId,
                new DamageContribution(accumulatedDamage, recordedAt)
        );
    }

    Optional<CombatAttribution.Attribution> take(UUID victimId, long now) {
        forcedKillerByVictim.remove(victimId);
        Map<UUID, DamageContribution> contributions =
                contributionsByVictim.remove(victimId);
        if (contributions == null || contributions.isEmpty()) {
            return Optional.empty();
        }
        CombatAttribution.Attribution attribution = CombatAttribution.resolve(
                contributions.entrySet().stream()
                        .map(entry -> new CombatAttribution.Contribution(
                                entry.getKey(),
                                entry.getValue().damage(),
                                entry.getValue().lastContributionAt()
                        ))
                        .toList(),
                now
        );
        return attribution.hasEligibleContributor()
                ? Optional.of(attribution)
                : Optional.empty();
    }

    void discard(UUID victimId) {
        contributionsByVictim.remove(victimId);
        forcedKillerByVictim.remove(victimId);
    }

    void expire(long now, long maximumAgeMilliseconds) {
        contributionsByVictim.values().forEach(contributions ->
                contributions.entrySet().removeIf(entry ->
                        now - entry.getValue().lastContributionAt()
                                >= maximumAgeMilliseconds));
        contributionsByVictim.entrySet().removeIf(
                entry -> entry.getValue().isEmpty()
        );
        forcedKillerByVictim.keySet().removeIf(
                victimId -> !contributionsByVictim.containsKey(victimId)
        );
    }

    void clear() {
        contributionsByVictim.clear();
        forcedKillerByVictim.clear();
    }

    private record DamageContribution(
            double damage,
            long lastContributionAt
    ) {
    }
}
