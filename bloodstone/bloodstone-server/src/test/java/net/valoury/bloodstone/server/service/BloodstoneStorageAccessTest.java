package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.model.StorageType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneStorageAccessTest {

    @Test
    void rankHierarchyUnlocksOnlyEligiblePages() {
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.DEFAULT, StorageType.DEFAULT, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.DEFAULT, StorageType.IRON, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.GOLD, StorageType.IRON, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.GOLD, StorageType.GOLD, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.GOLD, StorageType.DIAMOND, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.DIAMOND, StorageType.EMERALD, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.EMERALD, StorageType.EMERALD, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.EMERALD, StorageType.EXTRA, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.DEFAULT, StorageType.EXTRA, true));
    }

    @Test
    void canonicalStorageKeyContainsOnlyPlayerAndType() {
        UUID playerId = UUID.randomUUID();
        assertEquals(
                new BloodstoneStorageAccess.StorageKey(playerId, StorageType.GOLD),
                new BloodstoneStorageAccess.StorageKey(playerId, StorageType.GOLD)
        );
    }
}
