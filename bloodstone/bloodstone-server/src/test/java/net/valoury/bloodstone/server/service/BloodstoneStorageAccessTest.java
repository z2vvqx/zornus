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
                BloodstoneRank.DEFAULT, StorageType.LEGATE, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.JUSTICAR, StorageType.LEGATE, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.JUSTICAR, StorageType.JUSTICAR, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.JUSTICAR, StorageType.REGENT, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.REGENT, StorageType.ARCHON, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.ARCHON, StorageType.ARCHON, false));
        assertFalse(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.ARCHON, StorageType.EXTRA, false));
        assertTrue(BloodstoneStorageAccess.isEligible(
                BloodstoneRank.DEFAULT, StorageType.EXTRA, true));
    }

    @Test
    void canonicalStorageKeyContainsOnlyPlayerAndType() {
        UUID playerId = UUID.randomUUID();
        assertEquals(
                new BloodstoneStorageAccess.StorageKey(playerId, StorageType.JUSTICAR),
                new BloodstoneStorageAccess.StorageKey(playerId, StorageType.JUSTICAR)
        );
    }

    @Test
    void replacementNamesPreserveExistingPersistenceKeys() {
        assertEquals("Legate", StorageType.LEGATE.displayName());
        assertEquals("Justicar", StorageType.JUSTICAR.displayName());
        assertEquals("Regent", StorageType.REGENT.displayName());
        assertEquals("Archon", StorageType.ARCHON.displayName());

        assertEquals("IRON", StorageType.LEGATE.persistenceKey());
        assertEquals("GOLD", StorageType.JUSTICAR.persistenceKey());
        assertEquals("DIAMOND", StorageType.REGENT.persistenceKey());
        assertEquals("EMERALD", StorageType.ARCHON.persistenceKey());
    }
}
