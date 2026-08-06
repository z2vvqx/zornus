package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.model.StorageType;

import java.util.Objects;
import java.util.UUID;

public final class BloodstoneStorageAccess {

    private BloodstoneStorageAccess() {
    }

    public static boolean isEligible(
            BloodstoneRank rank,
            StorageType storageType,
            boolean extraUnlocked
    ) {
        return switch (storageType) {
            case DEFAULT -> true;
            case LEGATE -> rank.isAtLeast(BloodstoneRank.LEGATE);
            case CAVALIER -> rank.isAtLeast(BloodstoneRank.CAVALIER);
            case ARCHON -> rank.isAtLeast(BloodstoneRank.ARCHON);
            case VALORIAN -> rank.isAtLeast(BloodstoneRank.VALORIAN);
            case EXTRA -> extraUnlocked;
        };
    }

    public record StorageKey(UUID playerId, StorageType storageType) {
        public StorageKey {
            Objects.requireNonNull(playerId, "Player ID cannot be null");
            Objects.requireNonNull(storageType, "Storage type cannot be null");
        }
    }
}
