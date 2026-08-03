package net.valoury.bloodstone.server.service;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneCombatServiceTest {

    @Test
    void effectAxeCooldownExpiresAfterExactlyTwentyTicks() {
        assertTrue(BloodstoneEffectAxeCombatService.isActivationReady(null, 1_000L));
        assertFalse(BloodstoneEffectAxeCombatService.isActivationReady(
                1_000L,
                1_000_000_999L
        ));
        assertTrue(BloodstoneEffectAxeCombatService.isActivationReady(
                1_000L,
                1_000_001_000L
        ));
    }

    @Test
    void thornsDamageProvidesAnEffectAxeActivationFallback() {
        assertTrue(BloodstoneEffectAxeCombatService.shouldActivateFromDamageEvent(
                DamageCause.THORNS
        ));
        assertFalse(BloodstoneEffectAxeCombatService.shouldActivateFromDamageEvent(
                DamageCause.ENTITY_ATTACK
        ));
        assertFalse(BloodstoneEffectAxeCombatService.shouldActivateFromDamageEvent(null));
    }

    @Test
    void selfShotArrowsAreCancelledToPreventBowBoosting() {
        UUID playerId = UUID.fromString("25cbdb82-4f0c-4cf5-a21c-25fc5c78b28f");
        UUID otherPlayerId = UUID.fromString("3d243e5a-07b7-4bfe-aa6e-871687e4ee5b");

        assertTrue(BloodstoneCombatService.shouldCancelSelfInflictedBowDamage(
                true,
                playerId,
                playerId
        ));
        assertFalse(BloodstoneCombatService.shouldCancelSelfInflictedBowDamage(
                true,
                playerId,
                otherPlayerId
        ));
        assertFalse(BloodstoneCombatService.shouldCancelSelfInflictedBowDamage(
                false,
                playerId,
                playerId
        ));
        assertFalse(BloodstoneCombatService.shouldCancelSelfInflictedBowDamage(
                true,
                playerId,
                null
        ));
    }

    @Test
    void combinedEffectAxesSplitTheExistingParticleTotalEvenly() {
        assertEquals(40, BloodstonePresentationService.effectAxeParticleCount(1));
        assertEquals(20, BloodstonePresentationService.effectAxeParticleCount(2));
    }

    @Test
    void combatCountdownUsesOneMonotonicDeadline() {
        long expiryNanoseconds = 15_000_000_000L;

        assertEquals(
                15,
                BloodstoneCombatTagService.remainingCombatSeconds(
                        expiryNanoseconds,
                        0L
                )
        );
        assertEquals(
                12,
                BloodstoneCombatTagService.remainingCombatSeconds(
                        expiryNanoseconds,
                        3_000_000_000L
                )
        );
        assertEquals(
                1,
                BloodstoneCombatTagService.remainingCombatSeconds(
                        expiryNanoseconds,
                        14_999_999_999L
                )
        );
        assertEquals(
                0,
                BloodstoneCombatTagService.remainingCombatSeconds(
                        expiryNanoseconds,
                        expiryNanoseconds
                )
        );
    }

    @Test
    void combatProgressUsesWholeDisplayedSecondsWithoutReachingTheNextLevel() {
        assertEquals(0.99F, BloodstoneCombatTagService.combatProgress(15));
        assertEquals(0.066F, BloodstoneCombatTagService.combatProgress(1), 0.0001F);
        assertEquals(0.0F, BloodstoneCombatTagService.combatProgress(0));
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
