package net.valoury.bloodstone.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

final class CombatResolutionTest {

    private static final UUID KILLER_ID = new UUID(0L, 1L);
    private static final UUID VICTIM_ID = new UUID(0L, 2L);

    @Test
    void rejectsKillerAsCarryPlayer() {
        assertThrows(IllegalArgumentException.class, () -> new CombatResolution(
                UUID.randomUUID(),
                KILLER_ID,
                VICTIM_ID,
                KILLER_ID,
                Set.of(),
                null,
                null,
                false,
                false,
                Instant.EPOCH
        ));
    }

    @Test
    void rejectsCarryPlayerWithoutAssist() {
        UUID carryPlayerId = new UUID(0L, 3L);

        assertThrows(IllegalArgumentException.class, () -> new CombatResolution(
                UUID.randomUUID(),
                KILLER_ID,
                VICTIM_ID,
                carryPlayerId,
                Set.of(),
                null,
                null,
                false,
                false,
                Instant.EPOCH
        ));
    }
}
