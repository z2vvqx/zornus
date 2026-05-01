package com.zornus.friends.proxy.model;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record PlayerRecord(
        @NonNull UUID playerUuid,
        @NonNull String username
) {
}
