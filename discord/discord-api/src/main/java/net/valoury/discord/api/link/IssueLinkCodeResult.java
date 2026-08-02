package net.valoury.discord.api.link;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public sealed interface IssueLinkCodeResult {
    record Issued(String code, Instant expiresAt) implements IssueLinkCodeResult {
        public Issued {
            code = Objects.requireNonNull(code, "Link code cannot be null");
            expiresAt = Objects.requireNonNull(expiresAt, "Code expiry cannot be null");
            if (code.isBlank()) {
                throw new IllegalArgumentException("Link code cannot be blank");
            }
        }
    }

    record AlreadyLinked() implements IssueLinkCodeResult {
    }

    record RateLimited(Duration retryAfter) implements IssueLinkCodeResult {
        public RateLimited {
            Objects.requireNonNull(retryAfter, "Retry duration cannot be null");
            if (retryAfter.isZero() || retryAfter.isNegative()) {
                throw new IllegalArgumentException("Retry duration must be positive");
            }
        }
    }
}
