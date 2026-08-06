package net.valoury.staff.proxy.model;

import net.valoury.shared.model.PlayerRecord;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record StaffInspection(
        @NonNull PlayerRecord target,
        @NonNull Optional<Instant> firstSeenAt,
        @NonNull Optional<Instant> lastSeenAt,
        long connectionCount,
        @NonNull List<ConnectionSummary> connections,
        @NonNull List<RelatedAccount> relatedAccounts
) {
    public StaffInspection {
        Objects.requireNonNull(target, "Target cannot be null");
        Objects.requireNonNull(firstSeenAt, "First seen timestamp cannot be null");
        Objects.requireNonNull(lastSeenAt, "Last seen timestamp cannot be null");
        Objects.requireNonNull(connections, "Connections cannot be null");
        Objects.requireNonNull(relatedAccounts, "Related accounts cannot be null");
        if (connectionCount < 0) {
            throw new IllegalArgumentException("Connection count cannot be negative");
        }
        connections = List.copyOf(connections);
        relatedAccounts = List.copyOf(relatedAccounts);
    }
}
