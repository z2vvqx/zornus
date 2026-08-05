package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodDropPickupProtectionServiceTest {

    private static final UUID FIRST_PLAYER_ID = UUID.fromString(
            "e1244581-f430-41db-8cbe-30c54039e88a"
    );
    private static final UUID SECOND_PLAYER_ID = UUID.fromString(
            "2e8c8e3a-ebf9-44dc-9067-bcd31ff8ee6f"
    );

    @Test
    void dropsFromTheSameVictimMayMerge() {
        assertFalse(BloodDropPickupProtectionService
                .arePickupRestrictionsConflicting(
                        FIRST_PLAYER_ID,
                        FIRST_PLAYER_ID
                ));
    }

    @Test
    void unrestrictedDropsMayMerge() {
        assertFalse(BloodDropPickupProtectionService
                .arePickupRestrictionsConflicting(null, null));
    }

    @Test
    void dropsFromDifferentVictimsCannotMerge() {
        assertTrue(BloodDropPickupProtectionService
                .arePickupRestrictionsConflicting(
                        FIRST_PLAYER_ID,
                        SECOND_PLAYER_ID
                ));
    }

    @Test
    void protectedAndUnrestrictedDropsCannotMerge() {
        assertTrue(BloodDropPickupProtectionService
                .arePickupRestrictionsConflicting(FIRST_PLAYER_ID, null));
        assertTrue(BloodDropPickupProtectionService
                .arePickupRestrictionsConflicting(null, FIRST_PLAYER_ID));
    }
}
