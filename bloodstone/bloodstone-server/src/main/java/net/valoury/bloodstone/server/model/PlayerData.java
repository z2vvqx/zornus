package net.valoury.bloodstone.server.model;

import java.util.Objects;

public record PlayerData(PlayerProfile profile) {
    public PlayerData {
        Objects.requireNonNull(profile, "Profile cannot be null");
    }
}
