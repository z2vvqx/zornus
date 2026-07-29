package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CombatAnnouncementProgressionTest {

    @Test
    void usesTheSameEightTiersWithDominationStartingAtThreeKills() {
        assertEquals(
                List.of(5, 10, 15, 20, 25, 50, 75, 100),
                announcedKillCounts(
                        120,
                        CombatAnnouncementProgression::isRampageMilestone
                )
        );
        assertEquals(
                List.of(3, 6, 9, 12, 15, 30, 45, 60),
                announcedKillCounts(
                        120,
                        CombatAnnouncementProgression::isDominationMilestone
                )
        );
    }

    private static List<Integer> announcedKillCounts(
            int maximumKillCount,
            IntPredicate milestonePredicate
    ) {
        return IntStream.rangeClosed(1, maximumKillCount)
                .filter(milestonePredicate)
                .boxed()
                .toList();
    }
}
