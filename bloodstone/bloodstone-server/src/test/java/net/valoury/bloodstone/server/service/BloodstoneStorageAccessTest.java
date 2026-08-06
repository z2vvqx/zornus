package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.model.StorageType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class BloodstoneStorageAccessTest {

    @Test
    void rankHierarchyUnlocksOnlyEligiblePages() {
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.LEGATE, StorageType.DEFAULT, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.LEGATE, StorageType.LEGATE, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.LEGATE, StorageType.CAVALIER, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.CAVALIER, StorageType.LEGATE, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.CAVALIER, StorageType.CAVALIER, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.CAVALIER, StorageType.ARCHON, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.ARCHON, StorageType.VALORIAN, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.VALORIAN, StorageType.VALORIAN, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.VALORIAN, StorageType.EXTRA, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.LEGATE, StorageType.EXTRA, true));
    }

    @Test
    void canonicalStorageKeyContainsOnlyPlayerAndType() {
        UUID playerId = UUID.randomUUID();
        assertEquals(
                new BloodstoneStorageAccess.StorageKey(playerId, StorageType.CAVALIER),
                new BloodstoneStorageAccess.StorageKey(playerId, StorageType.CAVALIER)
        );
    }

    @Test
    void rankStorageNamesAreDirect() {
        assertEquals("Legate", StorageType.LEGATE.displayName());
        assertEquals("Cavalier", StorageType.CAVALIER.displayName());
        assertEquals("Archon", StorageType.ARCHON.displayName());
        assertEquals("Valorian", StorageType.VALORIAN.displayName());
    }
}
