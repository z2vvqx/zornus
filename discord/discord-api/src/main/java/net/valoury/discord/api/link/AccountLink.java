package net.valoury.discord.api.link;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static net.valoury.discord.api.ApiConstants.MINECRAFT_NAME_PATTERN;

public record AccountLink(
        UUID minecraftUniqueId,
        String minecraftName,
        long discordUserId,
        Instant linkedAt
) {
    public AccountLink {
        minecraftUniqueId = Objects.requireNonNull(
                minecraftUniqueId, "Minecraft unique identifier cannot be null");
        minecraftName = Objects.requireNonNull(minecraftName, "Minecraft name cannot be null");
        linkedAt = Objects.requireNonNull(linkedAt, "Link time cannot be null");
        if (!MINECRAFT_NAME_PATTERN.matcher(minecraftName).matches()) {
            throw new IllegalArgumentException("Minecraft name is invalid");
        }
        if (discordUserId <= 0) {
            throw new IllegalArgumentException("Discord user identifier must be positive");
        }
    }
}
