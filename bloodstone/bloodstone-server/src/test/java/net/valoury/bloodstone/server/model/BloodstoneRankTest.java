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
                "valoury.rank.iron",
                "valoury.rank.gold",
                "valoury.rank.emerald"
        );

        assertEquals(BloodstoneRank.EMERALD,
                BloodstoneRank.resolvePermissions(permissions::contains));
        assertEquals(BloodstoneRank.DEFAULT,
                BloodstoneRank.resolvePermissions(ignored -> false));
    }

    @Test
    void rankEconomicsMatchThePersistentGame() {
        assertRank(BloodstoneRank.DEFAULT, 3, 0, 36, null);
        assertRank(BloodstoneRank.IRON, 4, 4, 30, Duration.ofMinutes(10));
        assertRank(BloodstoneRank.GOLD, 5, 6, 24,
                Duration.ofMinutes(7).plusSeconds(30));
        assertRank(BloodstoneRank.DIAMOND, 6, 8, 18, Duration.ofMinutes(5));
        assertRank(BloodstoneRank.EMERALD, 7, 10, 12,
                Duration.ofMinutes(2).plusSeconds(30));
        assertFalse(BloodstoneRank.DEFAULT.isPaid());
        assertTrue(BloodstoneRank.IRON.isPaid());
    }

    private void assertRank(
            BloodstoneRank rank,
            int bloodPerHit,
            int freeBoxes,
            int boxCost,
            Duration cooldown
    ) {
        assertEquals(bloodPerHit, rank.bloodPerQualifyingHit());
        assertEquals(freeBoxes, rank.freeRandomBoxes());
        assertEquals(boxCost, rank.randomBoxBloodCost());
        assertEquals(java.util.Optional.ofNullable(cooldown), rank.enchanterCooldown());
    }
}
