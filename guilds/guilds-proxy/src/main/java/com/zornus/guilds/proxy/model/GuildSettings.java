package com.zornus.guilds.proxy.model;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record GuildSettings(
        @NonNull UUID playerId,
        @NonNull String invitePrivacy,
        boolean showChat
) {

    public GuildSettings(@NonNull UUID playerId) {
        this(playerId, "all", true);
    }
}
