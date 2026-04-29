package com.zornus.friends.proxy.model;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FriendRequest(
        @NotNull UUID senderUuid,
        @NotNull String senderUsername,
        @NotNull UUID receiverUuid,
        @NotNull String receiverUsername,
        @NotNull Instant timestamp
) {
    public FriendRequest {
    }

    public FriendRequest(@NotNull UUID senderUuid, @NotNull String senderUsername,
                         @NotNull UUID receiverUuid, @NotNull String receiverUsername) {
        this(senderUuid, senderUsername, receiverUuid, receiverUsername, Instant.now());
    }

    public FriendRequest(@NotNull UUID senderUuid, @NotNull UUID receiverUuid) {
        this(senderUuid, "", receiverUuid, "", Instant.now());
    }

    public boolean isExpired(@NotNull Duration expiry) {
        return timestamp.plus(expiry).isBefore(Instant.now());
    }

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
