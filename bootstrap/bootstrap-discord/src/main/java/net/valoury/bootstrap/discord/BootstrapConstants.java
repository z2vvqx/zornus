package net.valoury.bootstrap.discord;

import java.time.Duration;

public final class BootstrapConstants {
    public static final Duration DISCORD_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private BootstrapConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
