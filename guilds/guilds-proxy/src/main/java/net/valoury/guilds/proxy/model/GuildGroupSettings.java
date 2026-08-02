package net.valoury.guilds.proxy.model;

import net.valoury.shared.model.GroupJoinPolicy;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record GuildGroupSettings(
        @NonNull UUID guildId,
        @NonNull GroupJoinPolicy joinPolicy
) {

    public GuildGroupSettings(@NonNull UUID guildId) {
        this(guildId, GroupJoinPolicy.PRIVATE);
    }
}
