package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CombatAttributionTest {

    private static final UUID LOWER_UUID = new UUID(0L, 1L);
    private static final UUID MIDDLE_UUID = new UUID(0L, 2L);
    private static final UUID HIGHER_UUID = new UUID(0L, 3L);

    @Test
    void contributionsExpireExactlyAtFifteenSeconds() {
        assertTrue(CombatAttribution.isActive(5_001L, 20_000L));
        assertFalse(CombatAttribution.isActive(5_000L, 20_000L));
    }

    @Test
    void selectsKillerCarryAndAssistsDeterministically() {
        CombatAttribution.Attribution attribution = CombatAttribution.resolve(List.of(
                contribution(HIGHER_UUID, 10.0D, 19_000L),
                contribution(LOWER_UUID, 10.0D, 19_000L),
                contribution(MIDDLE_UUID, 4.0D, 19_500L),
                contribution(new UUID(0L, 4L), 100.0D, 5_000L)
        ), 20_000L);

        assertEquals(MIDDLE_UUID, attribution.killerId());
        assertEquals(LOWER_UUID, attribution.carryId());
        assertEquals(List.of(LOWER_UUID, HIGHER_UUID), attribution.assistIds());
        assertEquals(24.0D, attribution.totalDamage());
    }

    @Test
    void doesNotAwardCarryWhenKillerDealtTheMostDamage() {
        CombatAttribution.Attribution attribution = CombatAttribution.resolve(List.of(
                contribution(MIDDLE_UUID, 10.0D, 19_500L),
                contribution(LOWER_UUID, 4.0D, 19_000L),
                contribution(HIGHER_UUID, 3.0D, 19_000L)
        ), 20_000L);

        assertEquals(MIDDLE_UUID, attribution.killerId());
        assertNull(attribution.carryId());
        assertEquals(List.of(LOWER_UUID, HIGHER_UUID), attribution.assistIds());
    }

    @Test
    void doesNotAwardCarryForSoloKill() {
        CombatAttribution.Attribution attribution = CombatAttribution.resolve(List.of(
                contribution(MIDDLE_UUID, 10.0D, 19_500L)
        ), 20_000L);

        assertEquals(MIDDLE_UUID, attribution.killerId());
        assertNull(attribution.carryId());
        assertTrue(attribution.assistIds().isEmpty());
    }

    @Test
    void healingDistributesTheFullTwentyHealthPointPool() {
        assertEquals(5.0D, CombatAttribution.healing(5.0D, 20.0D));
        assertEquals(15.0D, CombatAttribution.healing(15.0D, 20.0D));
        assertEquals(20.0D,
                CombatAttribution.healing(5.0D, 20.0D)
                        + CombatAttribution.healing(15.0D, 20.0D));
    }

    private CombatAttribution.Contribution contribution(
            UUID attackerId,
            double damage,
            long lastContributionAt
    ) {
        return new CombatAttribution.Contribution(attackerId, damage, lastContributionAt);
    }
}
