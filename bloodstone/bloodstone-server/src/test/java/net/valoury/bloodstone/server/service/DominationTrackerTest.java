package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DominationTrackerTest {

    @Test
    void creditsThirdKillAndReannouncesAtEquivalentRampageTiers() {
        DominationTracker tracker = new DominationTracker();
        UUID killer = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        assertFalse(tracker.recordKill(killer, victim).announceDomination());
        assertFalse(tracker.recordKill(killer, victim).announceDomination());
        DominationTracker.Outcome third = tracker.recordKill(killer, victim);
        assertTrue(third.dominationCredit());
        assertTrue(third.announceDomination());
        tracker.recordKill(killer, victim);
        tracker.recordKill(killer, victim);
        DominationTracker.Outcome sixth = tracker.recordKill(killer, victim);
        assertFalse(sixth.dominationCredit());
        assertTrue(sixth.announceDomination());

        DominationTracker.Outcome eighteenth = sixth;
        for (int killCount = 7; killCount <= 18; killCount++) {
            eighteenth = tracker.recordKill(killer, victim);
        }
        assertFalse(eighteenth.announceDomination());

        DominationTracker.Outcome thirtieth = eighteenth;
        for (int killCount = 19; killCount <= 30; killCount++) {
            thirtieth = tracker.recordKill(killer, victim);
        }
        assertTrue(thirtieth.announceDomination());
    }

    @Test
    void reverseKillCreditsRevengeAndClearsDominatingChain() {
        DominationTracker tracker = new DominationTracker();
        UUID dominator = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        tracker.recordKill(dominator, victim);
        tracker.recordKill(dominator, victim);
        tracker.recordKill(dominator, victim);
        assertEquals(
                List.of(new DominationTracker.ActiveDomination(
                        dominator,
                        victim
                )),
                tracker.activeDominations()
        );

        assertTrue(tracker.recordKill(victim, dominator).revengeCredit());
        assertTrue(tracker.activeDominations().isEmpty());
        assertFalse(tracker.recordKill(victim, dominator).revengeCredit());
    }
}
