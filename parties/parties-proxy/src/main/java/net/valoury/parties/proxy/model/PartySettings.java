package net.valoury.parties.proxy.model;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record PartySettings(
        @NonNull UUID playerId,
        boolean allowChat,
        boolean allowWarp,
        boolean autoWarp,
        @NonNull String invitePrivacy
) {

    public PartySettings(@NonNull UUID playerId) {
        this(playerId, true, true, false, "all");
    }
}
