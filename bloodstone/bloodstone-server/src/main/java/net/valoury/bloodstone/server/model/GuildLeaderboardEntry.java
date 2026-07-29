package net.valoury.bloodstone.server.model;

import java.util.Objects;
import java.util.UUID;

public record GuildLeaderboardEntry(UUID guildId, long value) {
    public GuildLeaderboardEntry {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
    }
}
