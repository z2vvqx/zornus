package net.valoury.discord.api.evidence;

import java.time.Instant;

public record EvidenceSettings(
        long guildId,
        long forumChannelId,
        long reviewerRoleId,
        Instant updatedAt
) {
    public EvidenceSettings {
        if (guildId <= 0 || forumChannelId <= 0 || reviewerRoleId <= 0) {
            throw new IllegalArgumentException("Evidence Discord identifiers must be positive");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("Evidence settings update time cannot be null");
        }
    }
}
