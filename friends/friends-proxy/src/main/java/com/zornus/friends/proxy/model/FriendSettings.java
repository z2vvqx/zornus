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
    public FriendSettings {
    }

    public FriendSettings(@NonNull UUID playerId) {
        this(playerId, PresenceState.ONLINE, true, true, true, false, true);
    }

    public @NonNull FriendSettings withPresenceState(@NonNull PresenceState value) {
        return new FriendSettings(playerId, value, allowMessages, allowJump, showLastSeen, showLocation, allowRequests);
    }

    public @NonNull FriendSettings withAllowMessages(boolean value) {
        return new FriendSettings(playerId, presenceState, value, allowJump, showLastSeen, showLocation, allowRequests);
    }

    public @NonNull FriendSettings withAllowJump(boolean value) {
        return new FriendSettings(playerId, presenceState, allowMessages, value, showLastSeen, showLocation, allowRequests);
    }

    public @NonNull FriendSettings withShowLastSeen(boolean value) {
        return new FriendSettings(playerId, presenceState, allowMessages, allowJump, value, showLocation, allowRequests);
    }

    public @NonNull FriendSettings withShowLocation(boolean value) {
        return new FriendSettings(playerId, presenceState, allowMessages, allowJump, showLastSeen, value, allowRequests);
    }

    public @NonNull FriendSettings withAllowRequests(boolean value) {
        return new FriendSettings(playerId, presenceState, allowMessages, allowJump, showLastSeen, showLocation, value);
    }
}
