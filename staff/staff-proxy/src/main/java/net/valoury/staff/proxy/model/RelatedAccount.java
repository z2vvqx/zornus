package net.valoury.staff.proxy.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record RelatedAccount(
        @NonNull UUID playerUuid,
        @NonNull String username,
        int connectionDepth,
        @NonNull Set<AddressFingerprint> directlySharedAddressFingerprints,
        @Nullable String connectedThroughUsername,
        @NonNull Instant firstSeenAt,
        @NonNull Instant lastSeenAt
) {
    public RelatedAccount {
        Objects.requireNonNull(playerUuid, "Player UUID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(
                directlySharedAddressFingerprints,
                "Directly shared address fingerprints cannot be null"
        );
        Objects.requireNonNull(firstSeenAt, "First seen timestamp cannot be null");
        Objects.requireNonNull(lastSeenAt, "Last seen timestamp cannot be null");
        directlySharedAddressFingerprints = Set.copyOf(directlySharedAddressFingerprints);
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (connectionDepth <= 0) {
            throw new IllegalArgumentException("Connection depth must be positive");
        }
        if (firstSeenAt.isAfter(lastSeenAt)) {
            throw new IllegalArgumentException("First seen timestamp cannot follow last seen");
        }
        if (connectionDepth == 1 && directlySharedAddressFingerprints.isEmpty()) {
            throw new IllegalArgumentException(
                    "Direct connections require a shared address fingerprint"
            );
        }
        if (connectionDepth > 1 && !directlySharedAddressFingerprints.isEmpty()) {
            throw new IllegalArgumentException(
                    "Indirect connections cannot have a directly shared address fingerprint"
            );
        }
        if (connectionDepth == 1 && connectedThroughUsername != null) {
            throw new IllegalArgumentException("Direct connections cannot have an intermediate account");
        }
        if (connectionDepth > 1
                && (connectedThroughUsername == null || connectedThroughUsername.isBlank())) {
            throw new IllegalArgumentException("Indirect connections require an intermediate account");
        }
    }

    public boolean direct() {
        return connectionDepth == 1;
    }

    public int directlySharedConnectionCount() {
        return directlySharedAddressFingerprints.size();
    }
}
