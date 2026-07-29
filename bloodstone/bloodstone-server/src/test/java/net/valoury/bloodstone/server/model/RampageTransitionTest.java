package net.valoury.bloodstone.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RampageTransitionTest {

    @Test
    void normalKillOnlySetsARecordWhenBestIsExceeded() {
        RampageTransition belowBest = RampageTransition.afterKill(3, 8);
        assertEquals(4, belowBest.current());
        assertEquals(8, belowBest.best());
        assertFalse(belowBest.newBest());

        RampageTransition newBest = RampageTransition.afterKill(8, 8);
        assertEquals(9, newBest.current());
        assertEquals(9, newBest.best());
        assertTrue(newBest.newBest());
    }

    @Test
    void sameGuildDeathResetOccursBeforeGuildKill() {
        RampageTransition transition = RampageTransition.afterDeathThenKill(12);
        assertEquals(1, transition.current());
        assertEquals(12, transition.best());
        assertFalse(transition.newBest());
    }
}
