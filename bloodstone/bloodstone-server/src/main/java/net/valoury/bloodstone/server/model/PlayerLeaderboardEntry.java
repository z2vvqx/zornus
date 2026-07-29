package net.valoury.bloodstone.server.model;

import java.util.Objects;
import java.util.UUID;

public record PlayerLeaderboardEntry(UUID playerId, String username, long value) {
    public PlayerLeaderboardEntry {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
    }
}
