package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneCombatServiceTest {

    @Test
    void effectAxeCooldownExpiresAfterExactlyTwentyTicks() {
        assertTrue(BloodstoneCombatService.isEffectAxeActivationReady(null, 1_000L));
        assertFalse(BloodstoneCombatService.isEffectAxeActivationReady(
                1_000L,
                1_000_000_999L
        ));
        assertTrue(BloodstoneCombatService.isEffectAxeActivationReady(
                1_000L,
                1_000_001_000L
        ));
    }

    @Test
    void combatCountdownUsesOneMonotonicDeadline() {
        long expiryNanoseconds = 15_000_000_000L;

        assertEquals(
                15,
                BloodstoneCombatService.remainingCombatSeconds(
                        expiryNanoseconds,
                        0L
                )
        );
        assertEquals(
                12,
                BloodstoneCombatService.remainingCombatSeconds(
                        expiryNanoseconds,
                        3_000_000_000L
                )
        );
        assertEquals(
                1,
                BloodstoneCombatService.remainingCombatSeconds(
                        expiryNanoseconds,
                        14_999_999_999L
                )
        );
        assertEquals(
                0,
                BloodstoneCombatService.remainingCombatSeconds(
                        expiryNanoseconds,
                        expiryNanoseconds
                )
        );
    }

    @Test
    void combatProgressUsesWholeDisplayedSecondsWithoutReachingTheNextLevel() {
        assertEquals(0.99F, BloodstoneCombatService.combatProgress(15));
        assertEquals(0.066F, BloodstoneCombatService.combatProgress(1), 0.0001F);
        assertEquals(0.0F, BloodstoneCombatService.combatProgress(0));
    }

    @Test
    void bloodDropsUseExactlyHalfOfTheRandomRange() {
        assertTrue(BloodstoneCombatService.isBloodDropRoll(0.0D));
        assertTrue(BloodstoneCombatService.isBloodDropRoll(0.499_999D));
        assertFalse(BloodstoneCombatService.isBloodDropRoll(0.5D));
        assertFalse(BloodstoneCombatService.isBloodDropRoll(0.999_999D));
        assertThrows(
                IllegalArgumentException.class,
                () -> BloodstoneCombatService.isBloodDropRoll(1.0D)
        );
    }

    @Test
    void arrowFeedbackOnlyQualifiesAtOrAboveTheVictimsEyes() {
        assertFalse(BloodstoneCombatService.isHeadshot(63.999D, 64.0D));
        assertTrue(BloodstoneCombatService.isHeadshot(64.0D, 64.0D));
        assertTrue(BloodstoneCombatService.isHeadshot(64.001D, 64.0D));
    }
}
