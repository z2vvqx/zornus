package net.valoury.discord.api.link;

import java.time.Duration;
import java.util.Objects;

public sealed interface ConsumeLinkCodeResult {
    record Linked(AccountLink accountLink) implements ConsumeLinkCodeResult {
        public Linked {
            accountLink = Objects.requireNonNull(accountLink, "Account link cannot be null");
        }
    }

    record AlreadyLinked() implements ConsumeLinkCodeResult {
    }

    record MinecraftAccountLinkedElsewhere() implements ConsumeLinkCodeResult {
    }

    record DiscordAccountLinkedElsewhere() implements ConsumeLinkCodeResult {
    }

    record InvalidOrExpiredCode() implements ConsumeLinkCodeResult {
    }

    record RateLimited(Duration retryAfter) implements ConsumeLinkCodeResult {
        public RateLimited {
            Objects.requireNonNull(retryAfter, "Retry duration cannot be null");
            if (retryAfter.isZero() || retryAfter.isNegative()) {
                throw new IllegalArgumentException("Retry duration must be positive");
            }
        }
    }
}
