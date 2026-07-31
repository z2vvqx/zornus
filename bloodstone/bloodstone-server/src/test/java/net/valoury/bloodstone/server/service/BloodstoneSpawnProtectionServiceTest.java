package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneSpawnProtectionServiceTest {

    @Test
    void recognizesSpawnEquivalentRegionsCaseInsensitively() {
        List.of("spawn", "legate", "justicar", "regent", "archon")
                .forEach(regionName -> {
                    assertTrue(BloodstoneSpawnProtectionService.isSpawnRegion(regionName));
                    assertTrue(BloodstoneSpawnProtectionService.isSpawnRegion(
                            regionName.toUpperCase(Locale.ROOT)));
                });
    }

    @Test
    void rejectsOtherRegionNames() {
        assertFalse(BloodstoneSpawnProtectionService.isSpawnRegion("arena"));
        assertFalse(BloodstoneSpawnProtectionService.isSpawnRegion(""));
        assertFalse(BloodstoneSpawnProtectionService.isSpawnRegion(null));
    }
}
