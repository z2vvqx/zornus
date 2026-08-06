package net.valoury.staff.proxy.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;

public record ConnectionSummary(
        @NonNull AddressFingerprint addressFingerprint,
        @NonNull Instant firstSeenAt,
        @NonNull Instant lastSeenAt,
        long connectionCount,
        int associatedAccountCount
) {
    public ConnectionSummary {
        Objects.requireNonNull(addressFingerprint, "Address fingerprint cannot be null");
        Objects.requireNonNull(firstSeenAt, "First seen timestamp cannot be null");
        Objects.requireNonNull(lastSeenAt, "Last seen timestamp cannot be null");
        if (firstSeenAt.isAfter(lastSeenAt)) {
            throw new IllegalArgumentException("First seen timestamp cannot follow last seen timestamp");
        }
        if (connectionCount <= 0) {
            throw new IllegalArgumentException("Connection count must be positive");
        }
        if (associatedAccountCount <= 0) {
            throw new IllegalArgumentException("Associated account count must be positive");
        }
    }
}
