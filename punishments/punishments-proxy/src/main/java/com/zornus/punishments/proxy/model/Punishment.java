package com.zornus.punishments.proxy.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record Punishment(
        @NonNull String identifier,
        @NonNull PunishmentType type,
        @NonNull UUID punishedPlayerId,
        @Nullable UUID imposingPlayerId,
        @NonNull String reason,
        @NonNull Instant createdAt,
        @Nullable Instant expiresAt,
        boolean active,
        @Nullable Instant revokedAt,
        @Nullable UUID revokingPlayerId,
        @Nullable String revocationReason,
        boolean victimNotified,
        @Nullable String presetName,
        @Nullable Integer presetApplicationNumber
) {
    public Punishment {
        if ((presetName == null) != (presetApplicationNumber == null)) {
            throw new IllegalArgumentException(
                    "Preset name and application number must either both be present or both be absent");
        }
        if (presetApplicationNumber != null && presetApplicationNumber < 1) {
            throw new IllegalArgumentException("Preset application number must be positive");
        }
    }

    public boolean isPermanent() {
        return expiresAt == null && type != PunishmentType.KICK;
    }
}
