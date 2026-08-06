package net.valoury.staff.proxy.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConnectionEdge(
        @NonNull UUID playerUuid,
        @NonNull String username,
        @NonNull AddressFingerprint addressFingerprint,
        @NonNull Instant firstSeenAt,
        @NonNull Instant lastSeenAt,
        long connectionCount
) {
    public ConnectionEdge {
        Objects.requireNonNull(playerUuid, "Player UUID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(addressFingerprint, "Address fingerprint cannot be null");
        Objects.requireNonNull(firstSeenAt, "First seen timestamp cannot be null");
        Objects.requireNonNull(lastSeenAt, "Last seen timestamp cannot be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (firstSeenAt.isAfter(lastSeenAt)) {
            throw new IllegalArgumentException("First seen timestamp cannot follow last seen timestamp");
        }
        if (connectionCount <= 0) {
            throw new IllegalArgumentException("Connection count must be positive");
        }
    }
}
