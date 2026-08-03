package net.valoury.bloodstone.server.model;

import net.valoury.bloodstone.server.BloodstonePlayerIdentity;

import java.util.Objects;
import java.util.UUID;

public record PlayerProfile(
        UUID playerId,
        String username,
        int kills,
        int deaths,
        int assists,
        int carries,
        int dominations,
        int revenges,
        int currentRampage,
        int bestRampage,
        boolean extraStorageUnlocked,
        long version
) {
    public PlayerProfile {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        BloodstonePlayerIdentity.requireValidUsername(username);
        if (kills < 0 || deaths < 0 || assists < 0 || carries < 0
                || dominations < 0 || revenges < 0
                || currentRampage < 0 || bestRampage < currentRampage || version < 0) {
            throw new IllegalArgumentException("Player profile values cannot be negative or inconsistent");
        }
    }

    public double ratio() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }
}
