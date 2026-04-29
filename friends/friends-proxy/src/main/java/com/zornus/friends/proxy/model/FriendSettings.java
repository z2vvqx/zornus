package com.zornus.friends.proxy.model;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record FriendSettings(
        @NotNull UUID playerId,
        @NotNull PresenceState presenceState,
        boolean allowMessages,
        boolean allowJump,
        boolean showLastSeen,
        boolean showLocation,
        boolean allowRequests
) {
    public FriendSettings {
    }

    public FriendSettings(@NotNull UUID playerId) {
        this(playerId, PresenceState.ONLINE, true, true, true, false, true);
    }

    public @NotNull FriendSettings withPresenceState(@NotNull PresenceState value) {
        return new FriendSettings(playerId, value, allowMessages, allowJump, showLastSeen, showLocation, allowRequests);
    }

    public @NotNull FriendSettings withAllowMessages(boolean value) {
        return new FriendSettings(playerId, presenceState, value, allowJump, showLastSeen, showLocation, allowRequests);
    }

    public @NotNull FriendSettings withAllowJump(boolean value) {
        return new FriendSettings(playerId, presenceState, allowMessages, value, showLastSeen, showLocation, allowRequests);
    }

    public @NotNull FriendSettings withShowLastSeen(boolean value) {
        return new FriendSettings(playerId, presenceState, allowMessages, allowJump, value, showLocation, allowRequests);
    }

    public @NotNull FriendSettings withShowLocation(boolean value) {
        return new FriendSettings(playerId, presenceState, allowMessages, allowJump, showLastSeen, value, allowRequests);
    }

    public @NotNull FriendSettings withAllowRequests(boolean value) {
        return new FriendSettings(playerId, presenceState, allowMessages, allowJump, showLastSeen, showLocation, value);
    }
}
