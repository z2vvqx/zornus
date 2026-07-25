package net.valoury.guilds.proxy.model;

import net.valoury.guilds.proxy.GuildProxyConstants;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record GuildInvitation(
        @NonNull UUID guildId,
        @NonNull UUID senderId,
        @NonNull UUID targetId,
        @NonNull Instant timestamp
) {

    public GuildInvitation(@NonNull UUID guildId, @NonNull UUID senderId, @NonNull UUID targetId) {
        this(guildId, senderId, targetId, Instant.now());
    }

    public boolean isExpired() {
        return timestamp.plus(GuildProxyConstants.INVITATION_EXPIRY).isBefore(Instant.now());
    }
}
