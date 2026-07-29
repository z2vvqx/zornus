package net.valoury.bloodstone.server.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record RandomBoxWindow(Instant windowStart, int freeUses) {

    private static final Duration WINDOW_DURATION = Duration.ofHours(24);

    public RandomBoxWindow {
        if (freeUses < 0) {
            throw new IllegalArgumentException("Free-use count cannot be negative");
        }
    }

    public Reservation reserve(int maximumFreeUses, Instant now) {
        if (maximumFreeUses < 0) {
            throw new IllegalArgumentException("Maximum free uses cannot be negative");
        }
        Objects.requireNonNull(now, "Reservation time cannot be null");
        if (maximumFreeUses == 0) {
            return new Reservation(false, this);
        }

        boolean expired = windowStart == null
                || !now.isBefore(windowStart.plus(WINDOW_DURATION));
        RandomBoxWindow current = expired ? new RandomBoxWindow(now, 0) : this;
        if (current.freeUses >= maximumFreeUses) {
            return new Reservation(false, current);
        }
        return new Reservation(
                true,
                new RandomBoxWindow(current.windowStart, current.freeUses + 1)
        );
    }

    public record Reservation(boolean freeUse, RandomBoxWindow updatedWindow) {
        public Reservation {
            Objects.requireNonNull(updatedWindow, "Updated window cannot be null");
        }
    }
}
