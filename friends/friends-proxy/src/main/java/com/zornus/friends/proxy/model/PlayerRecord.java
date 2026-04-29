package com.zornus.friends.proxy.model;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PlayerRecord(
        @NotNull UUID playerUuid,
        @NotNull String username
) {
}
