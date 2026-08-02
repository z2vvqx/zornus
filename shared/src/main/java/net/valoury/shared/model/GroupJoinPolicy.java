package net.valoury.shared.model;

import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;

public enum GroupJoinPolicy {
    PRIVATE("private"),
    PUBLIC("public");

    private final @NonNull String storedValue;

    GroupJoinPolicy(@NonNull String storedValue) {
        this.storedValue = storedValue;
    }

    public @NonNull String storedValue() {
        return storedValue;
    }

    public static @NonNull Optional<GroupJoinPolicy> fromInput(@NonNull String value) {
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        for (GroupJoinPolicy policy : values()) {
            if (policy.storedValue.equals(normalizedValue)) {
                return Optional.of(policy);
            }
        }
        return Optional.empty();
    }

    public static @NonNull GroupJoinPolicy fromStoredValue(@NonNull String value) {
        return fromInput(value)
                .orElseThrow(() -> new IllegalArgumentException("Unknown group join policy: " + value));
    }
}
