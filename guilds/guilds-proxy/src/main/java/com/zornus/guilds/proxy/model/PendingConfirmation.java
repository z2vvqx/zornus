package com.zornus.guilds.proxy.model;

import com.zornus.guilds.proxy.GuildProxyConstants;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record PendingConfirmation(
        @NonNull UUID playerId,
        @NonNull ConfirmationType type,
        @Nullable UUID targetId,
        @Nullable String newValue,
        @NonNull Instant timestamp
) {

    public PendingConfirmation(@NonNull UUID playerId, @NonNull ConfirmationType type, @Nullable UUID targetId, @Nullable String newValue) {
        this(playerId, type, targetId, newValue, Instant.now());
    }

    public boolean isExpired() {
        return timestamp.plus(GuildProxyConstants.CONFIRMATION_EXPIRY).isBefore(Instant.now());
    }
}
