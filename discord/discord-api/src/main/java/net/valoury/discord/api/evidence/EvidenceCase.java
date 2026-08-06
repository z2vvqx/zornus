package net.valoury.discord.api.evidence;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record EvidenceCase(
        UUID caseId,
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
        @Nullable Instant punishmentExpiresAt,
        EvidenceCaseStatus status,
        @Nullable Long guildId,
        @Nullable Long forumChannelId,
        @Nullable Long threadId,
        @Nullable Long starterMessageId,
        Instant createdAt
) {
    public boolean hasDiscordThread() {
        return guildId != null && forumChannelId != null && threadId != null && starterMessageId != null;
    }

    public @Nullable String threadJumpUrl() {
        return hasDiscordThread()
                ? "https://discord.com/channels/" + guildId + "/" + threadId
                : null;
    }
}
