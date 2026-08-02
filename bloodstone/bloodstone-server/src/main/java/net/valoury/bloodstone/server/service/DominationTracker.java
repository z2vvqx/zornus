package net.valoury.bloodstone.server.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DominationTracker {

    private final Map<DominationPair, Integer> killChains = new HashMap<>();

    public Outcome recordKill(UUID killerId, UUID victimId) {
        Objects.requireNonNull(killerId, "Killer ID cannot be null");
        Objects.requireNonNull(victimId, "Victim ID cannot be null");
        if (killerId.equals(victimId)) {
            throw new IllegalArgumentException("A player cannot dominate themselves");
        }

        Integer reverseCount = killChains.remove(new DominationPair(victimId, killerId));
        boolean revenge = reverseCount != null && reverseCount >= 4;
        int killCount = killChains.merge(
                new DominationPair(killerId, victimId),
                1,
                Integer::sum
        );
        return new Outcome(
                killCount,
                killCount == 4,
                revenge,
                CombatAnnouncementProgression.isDominationMilestone(killCount)
        );
    }

    public void clear() {
        killChains.clear();
    }

    public List<ActiveDomination> activeDominations() {
        return killChains.entrySet().stream()
                .filter(entry -> entry.getValue() >= 4)
                .map(entry -> new ActiveDomination(
                        entry.getKey().killerId(),
                        entry.getKey().victimId()
                ))
                .toList();
    }

    public record Outcome(
            int killCount,
            boolean dominationCredit,
            boolean revengeCredit,
            boolean announceDomination
    ) {
    }

    public record ActiveDomination(UUID dominatorId, UUID dominatedPlayerId) {
        public ActiveDomination {
            Objects.requireNonNull(dominatorId, "Dominator ID cannot be null");
            Objects.requireNonNull(
                    dominatedPlayerId,
                    "Dominated player ID cannot be null"
            );
        }
    }

    private record DominationPair(UUID killerId, UUID victimId) {
    }
}
