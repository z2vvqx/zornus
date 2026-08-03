package net.valoury.bloodstone.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstonePlayerIdentityTest {

    @Test
    void acceptsOnlyMinecraftUsernameCharactersAndLength() {
        assertTrue(BloodstonePlayerIdentity.isValidUsername("MMAJED"));
        assertTrue(BloodstonePlayerIdentity.isValidUsername("Player_Name123"));
        assertTrue(BloodstonePlayerIdentity.isValidUsername("A"));
        assertTrue(BloodstonePlayerIdentity.isValidUsername("1234567890123456"));

        assertFalse(BloodstonePlayerIdentity.isValidUsername(null));
        assertFalse(BloodstonePlayerIdentity.isValidUsername(""));
        assertFalse(BloodstonePlayerIdentity.isValidUsername("Player-Name"));
        assertFalse(BloodstonePlayerIdentity.isValidUsername("12345678901234567"));
        assertFalse(BloodstonePlayerIdentity.isValidUsername("e7161f11-1536-4f"));
    }

    @Test
    void rejectsInvalidPersistedUsernameInput() {
        assertDoesNotThrow(() ->
                BloodstonePlayerIdentity.requireValidUsername("MMAJED"));
        assertThrows(
                IllegalArgumentException.class,
                () -> BloodstonePlayerIdentity.requireValidUsername(
                        "e7161f11-1536-4f"
                )
        );
    }
}
