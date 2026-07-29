package net.valoury.bloodstone.server.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StorageSession(
        UUID playerId,
        StorageType storageType,
        UUID sessionToken,
        byte @Nullable [] contentsPayload,
        long version,
        Instant leaseExpiresAt
) {
    public StorageSession {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(storageType, "Storage type cannot be null");
        Objects.requireNonNull(sessionToken, "Session token cannot be null");
        Objects.requireNonNull(leaseExpiresAt, "Lease expiry cannot be null");
        if (version < 0) {
            throw new IllegalArgumentException("Storage version cannot be negative");
        }
        contentsPayload = copy(contentsPayload);
    }

    @Override
    public byte @Nullable [] contentsPayload() {
        return copy(contentsPayload);
    }

    private static byte @Nullable [] copy(byte @Nullable [] value) {
        return value == null ? null : value.clone();
    }
}
