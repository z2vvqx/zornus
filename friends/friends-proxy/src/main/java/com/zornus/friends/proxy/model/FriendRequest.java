package com.zornus.friends.proxy.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FriendRequest(
        @NonNull UUID senderUuid,
        @NonNull String senderUsername,
        @NonNull UUID receiverUuid,
        @NonNull String receiverUsername,
        @NonNull Instant timestamp
) {
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FriendRequest that = (FriendRequest) obj;
        return Objects.equals(senderUuid, that.senderUuid) &&
                Objects.equals(receiverUuid, that.receiverUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderUuid, receiverUuid);
    }
}
