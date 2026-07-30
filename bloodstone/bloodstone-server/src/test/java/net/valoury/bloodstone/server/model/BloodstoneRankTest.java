package net.valoury.bloodstone.server.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneRankTest {

    @Test
    void highestPermissionAlwaysWins() {
        Set<String> permissions = Set.of(
                "valoury.rank.legate",
                "valoury.rank.justicar",
                "valoury.rank.regent",
                "valoury.rank.archon"
        );

        assertEquals(BloodstoneRank.ARCHON,
                BloodstoneRank.resolvePermissions(permissions::contains));
        assertEquals(BloodstoneRank.DEFAULT,
                BloodstoneRank.resolvePermissions(ignored -> false));
    }

    @Test
    void eachPermissionResolvesItsReplacementRank() {
        assertPermission(BloodstoneRank.LEGATE, "valoury.rank.legate");
        assertPermission(BloodstoneRank.JUSTICAR, "valoury.rank.justicar");
        assertPermission(BloodstoneRank.REGENT, "valoury.rank.regent");
        assertPermission(BloodstoneRank.ARCHON, "valoury.rank.archon");
    }

    @Test
    void rankEconomicsMatchThePersistentGame() {
        assertRank(BloodstoneRank.DEFAULT, 3, 0, 36, null);
        assertRank(BloodstoneRank.LEGATE, 4, 4, 30, Duration.ofMinutes(10));
        assertRank(BloodstoneRank.JUSTICAR, 5, 6, 24,
                Duration.ofMinutes(7).plusSeconds(30));
        assertRank(BloodstoneRank.REGENT, 6, 8, 18, Duration.ofMinutes(5));
        assertRank(BloodstoneRank.ARCHON, 7, 10, 12,
                Duration.ofMinutes(2).plusSeconds(30));
        assertFalse(BloodstoneRank.DEFAULT.isPaid());
        assertTrue(BloodstoneRank.LEGATE.isPaid());
    }

    private void assertRank(
            BloodstoneRank rank,
            int bloodPerQualifyingHit,
            int freeRandomBoxes,
            int randomBoxBloodCost,
            Duration enchanterCooldown
    ) {
        assertEquals(bloodPerQualifyingHit, rank.bloodPerQualifyingHit());
        assertEquals(freeRandomBoxes, rank.freeRandomBoxes());
        assertEquals(randomBoxBloodCost, rank.randomBoxBloodCost());
        assertEquals(java.util.Optional.ofNullable(enchanterCooldown),
                rank.enchanterCooldown());
    }

    private void assertPermission(BloodstoneRank expectedRank, String permission) {
        assertEquals(expectedRank, BloodstoneRank.resolvePermissions(permission::equals));
    }
}
