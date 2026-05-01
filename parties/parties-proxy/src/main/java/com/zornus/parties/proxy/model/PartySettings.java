package com.zornus.parties.proxy.model;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record PartySettings(
        @NonNull UUID playerId,
        boolean allowChat,
        boolean allowWarp,
        @NonNull String invitePrivacy
) {

    public PartySettings(@NonNull UUID playerId) {
        this(playerId, true, true, "all");
    }
}
