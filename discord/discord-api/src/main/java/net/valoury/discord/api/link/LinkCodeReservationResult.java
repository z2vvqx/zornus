package net.valoury.discord.api.link;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public sealed interface LinkCodeReservationResult {
    record Reserved(Instant expiresAt) implements LinkCodeReservationResult {
        public Reserved {
            expiresAt = Objects.requireNonNull(expiresAt, "Code expiry cannot be null");
        }
    }

    record AlreadyLinked() implements LinkCodeReservationResult {
    }

    record RateLimited(Duration retryAfter) implements LinkCodeReservationResult {
        public RateLimited {
            Objects.requireNonNull(retryAfter, "Retry duration cannot be null");
            if (retryAfter.isZero() || retryAfter.isNegative()) {
                throw new IllegalArgumentException("Retry duration must be positive");
            }
        }
    }

    record CodeHashCollision() implements LinkCodeReservationResult {
    }
}
