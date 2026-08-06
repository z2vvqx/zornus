package net.valoury.bloodstone.server.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class BloodstoneRankTest {

    @Test
    void highestPermissionAlwaysWins() {
        Set<String> permissions = Set.of(
                "valoury.rank.legate",
                "valoury.rank.cavalier",
                "valoury.rank.archon",
                "valoury.rank.valorian"
        );

        assertEquals(BloodstoneRank.VALORIAN,
                BloodstoneRank.resolvePermissions(permissions::contains));
        assertEquals(BloodstoneRank.LEGATE,
                BloodstoneRank.resolvePermissions(ignored -> false));
    }

    @Test
    void onlyConfiguredRanksExist() {
        assertArrayEquals(
                new BloodstoneRank[]{
                        BloodstoneRank.VALORIAN,
                        BloodstoneRank.ARCHON,
                        BloodstoneRank.CAVALIER,
                        BloodstoneRank.LEGATE
                },
                BloodstoneRank.values()
        );
    }

    @Test
    void eachPermissionResolvesItsRank() {
        assertPermission(BloodstoneRank.LEGATE, "valoury.rank.legate");
        assertPermission(BloodstoneRank.CAVALIER, "valoury.rank.cavalier");
        assertPermission(BloodstoneRank.ARCHON, "valoury.rank.archon");
        assertPermission(BloodstoneRank.VALORIAN, "valoury.rank.valorian");
    }

    @Test
    void rankEconomicsMatchTheConfiguredHierarchy() {
        assertRank(BloodstoneRank.LEGATE, 4, 4, 30, Duration.ofMinutes(10));
        assertRank(BloodstoneRank.CAVALIER, 5, 6, 24,
                Duration.ofMinutes(7).plusSeconds(30));
        assertRank(BloodstoneRank.ARCHON, 6, 8, 18, Duration.ofMinutes(5));
        assertRank(BloodstoneRank.VALORIAN, 7, 10, 12,
                Duration.ofMinutes(2).plusSeconds(30));
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
        assertEquals(enchanterCooldown, rank.enchanterCooldown());
    }

    private void assertPermission(BloodstoneRank expectedRank, String permission) {
        assertEquals(expectedRank, BloodstoneRank.resolvePermissions(permission::equals));
    }
}
