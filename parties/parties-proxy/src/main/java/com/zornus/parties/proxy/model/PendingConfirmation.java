package com.zornus.parties.proxy.model;

import com.zornus.parties.proxy.PartyProxyConstants;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record PendingConfirmation(
        @NonNull UUID playerId,
        @NonNull ConfirmationType type,
        @Nullable UUID targetId,
        @Nullable String targetName,
        @NonNull Instant timestamp
) {

    public PendingConfirmation(@NonNull UUID playerId, @NonNull ConfirmationType type,
                               @Nullable UUID targetId, @Nullable String targetName) {
        this(playerId, type, targetId, targetName, Instant.now());
    }

    public boolean isExpired() {
        return timestamp.plus(PartyProxyConstants.CONFIRMATION_EXPIRY).isBefore(Instant.now());
    }
}
