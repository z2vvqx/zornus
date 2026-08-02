package net.valoury.discord.internal.storage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

record PendingLinkCode(
        UUID minecraftUniqueId,
        String minecraftName,
        Instant expiresAt
) {
    PendingLinkCode {
        minecraftUniqueId = Objects.requireNonNull(
                minecraftUniqueId, "Minecraft unique identifier cannot be null");
        minecraftName = Objects.requireNonNull(minecraftName, "Minecraft name cannot be null");
        expiresAt = Objects.requireNonNull(expiresAt, "Code expiry cannot be null");
    }
}
