package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CombatAttributionTrackerTest {

    private static final UUID VICTIM_ID = UUID.fromString(
            "bc3980a8-333a-458d-8058-cdf7a28500ab"
    );
    private static final UUID KILLER_ID = UUID.fromString(
            "2692727f-b1aa-40f1-b314-bce7adb37824"
    );

    @Test
    void discardRemovesForcedKillerAndContributionsTogether() {
        CombatAttributionTracker tracker = new CombatAttributionTracker();
        tracker.forceKiller(VICTIM_ID, KILLER_ID, 20.0D);

        tracker.discard(VICTIM_ID);

        assertFalse(tracker.isForcedKiller(VICTIM_ID, KILLER_ID));
        assertTrue(tracker.take(VICTIM_ID, System.currentTimeMillis()).isEmpty());
    }

    @Test
    void expiryCannotLeaveAStaleForcedKiller() {
        CombatAttributionTracker tracker = new CombatAttributionTracker();
        tracker.forceKiller(VICTIM_ID, KILLER_ID, 20.0D);

        tracker.expire(Long.MAX_VALUE, 1L);

        assertFalse(tracker.isForcedKiller(VICTIM_ID, KILLER_ID));
        assertTrue(tracker.take(VICTIM_ID, Long.MAX_VALUE).isEmpty());
    }
}
