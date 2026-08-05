package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DominationTrackerTest {

    @Test
    void creditsFourthKillAndReannouncesAtEquivalentRampageTiers() {
        DominationTracker tracker = new DominationTracker();
        UUID killer = UUID.randomUUID();
        UUID victim = UUID.randomUUID();

        assertFalse(tracker.recordKill(killer, victim).announceDomination());
        assertFalse(tracker.recordKill(killer, victim).announceDomination());
        assertFalse(tracker.recordKill(killer, victim).announceDomination());
        DominationTracker.Outcome fourth = tracker.recordKill(killer, victim);
        assertTrue(fourth.dominationCredit());
        assertTrue(fourth.announceDomination());
        for (int killCount = 5; killCount < 8; killCount++) {
            tracker.recordKill(killer, victim);
        }
        DominationTracker.Outcome eighth = tracker.recordKill(killer, victim);
        assertFalse(eighth.dominationCredit());
        assertTrue(eighth.announceDomination());
        for (int killCount = 9; killCount < 12; killCount++) {
            tracker.recordKill(killer, victim);
        }
        DominationTracker.Outcome twelfth = tracker.recordKill(killer, victim);
        assertFalse(twelfth.dominationCredit());
        assertTrue(twelfth.announceDomination());

        DominationTracker.Outcome twentyFourth = twelfth;
        for (int killCount = 13; killCount <= 24; killCount++) {
            twentyFourth = tracker.recordKill(killer, victim);
        }
        assertFalse(twentyFourth.announceDomination());

        DominationTracker.Outcome fortieth = twentyFourth;
        for (int killCount = 25; killCount <= 40; killCount++) {
            fortieth = tracker.recordKill(killer, victim);
        }
        assertTrue(fortieth.announceDomination());
    }

    @Test
    void reverseKillBeforeFourClearsChainWithoutCreditingRevenge() {
        DominationTracker tracker = new DominationTracker();
        UUID killer = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        tracker.recordKill(killer, victim);
        tracker.recordKill(killer, victim);
        tracker.recordKill(killer, victim);

        assertTrue(tracker.activeDominations().isEmpty());
        assertFalse(tracker.recordKill(victim, killer).revengeCredit());
        assertTrue(tracker.activeDominations().isEmpty());
    }

    @Test
    void reverseKillCreditsRevengeAndClearsDominatingChain() {
        DominationTracker tracker = new DominationTracker();
        UUID dominator = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        tracker.recordKill(dominator, victim);
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
        assertTrue(tracker.isActiveDomination(dominator, victim));
        assertFalse(tracker.isActiveDomination(victim, dominator));

        assertTrue(tracker.recordKill(victim, dominator).revengeCredit());
        assertTrue(tracker.activeDominations().isEmpty());
        assertFalse(tracker.isActiveDomination(dominator, victim));
        assertFalse(tracker.recordKill(victim, dominator).revengeCredit());
    }
}
