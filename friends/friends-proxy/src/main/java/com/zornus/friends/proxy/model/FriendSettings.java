package com.zornus.friends.proxy.model;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record FriendSettings(
        @NonNull UUID playerId,
        @NonNull PresenceState presenceState,
        boolean allowMessages,
        boolean allowJump,
        boolean showLastSeen,
        boolean showLocation,
        boolean allowRequests
) {
    public FriendSettings(@NonNull UUID playerId) {
        this(playerId, PresenceState.ONLINE, true, true, true, false, true);
    }
}
