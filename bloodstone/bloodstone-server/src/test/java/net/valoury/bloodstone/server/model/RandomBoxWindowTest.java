package net.valoury.bloodstone.server.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RandomBoxWindowTest {

    @Test
    void firstFreeUseStartsTheRollingWindow() {
        Instant firstUse = Instant.parse("2026-07-26T10:00:00Z");
        RandomBoxWindow.Reservation reservation =
                new RandomBoxWindow(null, 0).reserve(4, firstUse);

        assertTrue(reservation.freeUse());
        assertEquals(firstUse, reservation.updatedWindow().windowStart());
        assertEquals(1, reservation.updatedWindow().freeUses());
    }

    @Test
    void fullWindowResetsAtExactlyTwentyFourHours() {
        Instant start = Instant.parse("2026-07-26T10:00:00Z");
        RandomBoxWindow fullWindow = new RandomBoxWindow(start, 4);

        assertFalse(fullWindow.reserve(
                4,
                start.plus(Duration.ofHours(24)).minusNanos(1)
        ).freeUse());
        RandomBoxWindow.Reservation reset =
                fullWindow.reserve(4, start.plus(Duration.ofHours(24)));
        assertTrue(reset.freeUse());
        assertEquals(1, reset.updatedWindow().freeUses());
        assertEquals(start.plus(Duration.ofHours(24)),
                reset.updatedWindow().windowStart());
    }

    @Test
    void rankWithoutFreeUsesNeverStartsAWindow() {
        RandomBoxWindow unchanged = new RandomBoxWindow(null, 0);
        RandomBoxWindow.Reservation reservation =
                unchanged.reserve(0, Instant.parse("2026-07-26T10:00:00Z"));

        assertFalse(reservation.freeUse());
        assertEquals(unchanged, reservation.updatedWindow());
    }
}
