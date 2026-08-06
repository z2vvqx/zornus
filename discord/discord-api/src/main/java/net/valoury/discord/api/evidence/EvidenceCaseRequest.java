package net.valoury.discord.api.evidence;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record EvidenceCaseRequest(
        String punishmentIdentifier,
        UUID punishedPlayerId,
        String punishedPlayerName,
        @Nullable UUID issuingPlayerId,
        @Nullable Long issuingDiscordUserId,
        String presetName,
        int presetApplicationNumber,
        String punishmentType,
        String reason,
        Instant punishmentCreatedAt,
        @Nullable Instant punishmentExpiresAt
) {
}
