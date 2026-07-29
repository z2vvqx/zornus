package net.valoury.bloodstone.server.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlayerProfileTest {

    @Test
    void ratioUsesOneAsTheZeroDeathDivisor() {
        assertEquals(7.0D, profile(7, 0).ratio());
        assertEquals(2.5D, profile(5, 2).ratio());
        assertEquals(0.0D, profile(0, 0).ratio());
    }

    private PlayerProfile profile(int kills, int deaths) {
        return new PlayerProfile(
                UUID.randomUUID(),
                "TestPlayer",
                kills,
                deaths,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                0
        );
    }
}
