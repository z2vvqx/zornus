package net.valoury.shared.model;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record PlayerRecord(
        @NonNull UUID playerUuid,
        @NonNull String username
) {
}
