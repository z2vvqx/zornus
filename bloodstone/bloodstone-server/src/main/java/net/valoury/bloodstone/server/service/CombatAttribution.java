package net.valoury.bloodstone.server.service;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CombatAttribution {

    private static final Duration CONTRIBUTION_LIFETIME = Duration.ofSeconds(15);
    private static final double HEALING_POOL = 20.0D;

    private CombatAttribution() {
    }

    public static Attribution resolve(List<Contribution> contributions, long nowMilliseconds) {
        List<Contribution> eligible = contributions.stream()
                .filter(contribution -> isActive(
                        contribution.lastContributionAt(),
                        nowMilliseconds
                ))
                .toList();
        if (eligible.isEmpty()) {
            return Attribution.empty();
        }

        Comparator<Contribution> stableNewest = Comparator
                .comparingLong(Contribution::lastContributionAt)
                .thenComparing(Contribution::attackerId, Comparator.reverseOrder());
        Contribution killer = eligible.stream().max(stableNewest).orElseThrow();
        Contribution carry = eligible.stream()
                .max(Comparator.comparingDouble(Contribution::damage)
                        .thenComparing(stableNewest))
                .orElseThrow();
        List<UUID> assists = eligible.stream()
                .map(Contribution::attackerId)
                .filter(attackerId -> !attackerId.equals(killer.attackerId()))
                .sorted()
                .toList();
        double totalDamage = eligible.stream().mapToDouble(Contribution::damage).sum();
        return new Attribution(
                killer.attackerId(),
                carry.attackerId(),
                assists,
                eligible,
                totalDamage
        );
    }

    public static boolean isActive(long contributionAt, long nowMilliseconds) {
        return nowMilliseconds - contributionAt
                < CONTRIBUTION_LIFETIME.toMillis();
    }

    public static double healing(double damage, double totalDamage) {
        if (damage < 0.0D || totalDamage <= 0.0D || damage > totalDamage) {
            throw new IllegalArgumentException("Damage values cannot produce proportional healing");
        }
        return damage / totalDamage * HEALING_POOL;
    }

    public record Contribution(UUID attackerId, double damage, long lastContributionAt) {
        public Contribution {
            Objects.requireNonNull(attackerId, "Attacker ID cannot be null");
            if (!Double.isFinite(damage) || damage < 0.0D) {
                throw new IllegalArgumentException("Contribution damage must be finite and non-negative");
            }
        }
    }

    public record Attribution(
            @Nullable UUID killerId,
            @Nullable UUID carryId,
            List<UUID> assistIds,
            List<Contribution> eligibleContributions,
            double totalDamage
    ) {
        public Attribution {
            assistIds = List.copyOf(assistIds);
            eligibleContributions = List.copyOf(eligibleContributions);
        }

        private static Attribution empty() {
            return new Attribution(null, null, List.of(), List.of(), 0.0D);
        }

        public boolean hasEligibleContributor() {
            return killerId != null;
        }
    }
}
