package net.valoury.bloodstone.server.storage;

import org.jspecify.annotations.Nullable;

public record CombatResolutionOutcome(
        boolean newlyApplied,
        int killerCurrentRampage,
        int killerBestRampage,
        boolean newPlayerBest,
        @Nullable Integer killerGuildCurrentRampage,
        @Nullable Integer killerGuildBestRampage,
        boolean newGuildBest
) {
    public CombatResolutionOutcome {
        if (killerCurrentRampage < 0 || killerBestRampage < killerCurrentRampage) {
            throw new IllegalArgumentException("Invalid killer rampage outcome");
        }
        if ((killerGuildCurrentRampage == null) != (killerGuildBestRampage == null)) {
            throw new IllegalArgumentException("Guild rampage values must both be present or absent");
        }
        if (killerGuildCurrentRampage != null
                && (killerGuildCurrentRampage < 0
                || killerGuildBestRampage < killerGuildCurrentRampage)) {
            throw new IllegalArgumentException("Invalid guild rampage outcome");
        }
        if (killerGuildCurrentRampage == null && newGuildBest) {
            throw new IllegalArgumentException("A guild best cannot be set without a guild outcome");
        }
    }
}
