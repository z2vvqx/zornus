package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.model.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstonePlayerSessionRegistryTest {

    private static final UUID PLAYER_ID =
            UUID.fromString("e7161f11-1536-4fcc-9639-937461677ee0");

    @Test
    void ignoresProfileCompletionAfterTheSessionEnds() {
        BloodstonePlayerSessionRegistry registry =
                new BloodstonePlayerSessionRegistry();
        UUID generation = registry.beginLoading(PLAYER_ID);
        registry.storeLoadedProfile(
                PLAYER_ID,
                generation,
                profile(1, "MMAJED")
        );
        registry.finishLoading(PLAYER_ID, generation, true);
        assertTrue(registry.isLoaded(PLAYER_ID));

        registry.endSession(PLAYER_ID);
        registry.updateProfileIfCurrent(
                PLAYER_ID,
                generation,
                profile(2, "MMAJED")
        );

        assertFalse(registry.isLoaded(PLAYER_ID));
        assertTrue(registry.profile(PLAYER_ID).isEmpty());
    }

    @Test
    void retainsTheNewestProfileFromTheCurrentConnectionGeneration() {
        BloodstonePlayerSessionRegistry registry =
                new BloodstonePlayerSessionRegistry();
        UUID firstGeneration = registry.beginLoading(PLAYER_ID);
        registry.storeLoadedProfile(
                PLAYER_ID,
                firstGeneration,
                profile(2, "MMAJED")
        );
        registry.finishLoading(PLAYER_ID, firstGeneration, true);

        registry.updateProfileIfCurrent(
                PLAYER_ID,
                firstGeneration,
                profile(1, "MMAJED")
        );
        assertEquals(2, registry.profile(PLAYER_ID).orElseThrow().version());

        UUID secondGeneration = registry.beginLoading(PLAYER_ID);
        registry.updateProfileIfCurrent(
                PLAYER_ID,
                firstGeneration,
                profile(3, "MMAJED")
        );
        assertTrue(registry.profile(PLAYER_ID).isEmpty());

        registry.storeLoadedProfile(
                PLAYER_ID,
                secondGeneration,
                profile(4, "MMAJED")
        );
        registry.finishLoading(PLAYER_ID, secondGeneration, true);
        assertEquals(4, registry.profile(PLAYER_ID).orElseThrow().version());
    }

    private static PlayerProfile profile(long version, String username) {
        return new PlayerProfile(
                PLAYER_ID,
                username,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                version
        );
    }
}
