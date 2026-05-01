package com.zornus.friends.proxy.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.time.Instant;
import java.util.UUID;

public record FriendRelation(
        @NonNull UUID player1Uuid,
        @NonNull String player1Username,
        @NonNull UUID player2Uuid,
        @NonNull String player2Username,
        @NonNull Instant friendsSince
) {
    public FriendRelation {
    }

    public FriendRelation(@NonNull UUID player1Uuid, @NonNull String player1Username,
                          @NonNull UUID player2Uuid, @NonNull String player2Username) {
        this(player1Uuid, player1Username, player2Uuid, player2Username, Instant.now());
    }

    public boolean involves(@NonNull UUID playerUuid) {
        return player1Uuid.equals(playerUuid) || player2Uuid.equals(playerUuid);
    }

    public @NonNull UUID getOtherPlayerUuid(@NonNull UUID playerUuid) {
        if (player1Uuid.equals(playerUuid)) {
            return player2Uuid;
        } else if (player2Uuid.equals(playerUuid)) {
            return player1Uuid;
        }
        throw new IllegalArgumentException("Player UUID " + playerUuid + " is not part of this relation");
    }

    public @NonNull String getOtherPlayerUsername(@NonNull UUID playerUuid) {
        if (player1Uuid.equals(playerUuid)) {
            return player2Username;
        } else if (player2Uuid.equals(playerUuid)) {
            return player1Username;
        }
        throw new IllegalArgumentException("Player UUID " + playerUuid + " is not part of this relation");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FriendRelation that = (FriendRelation) obj;
        return (Objects.equals(player1Uuid, that.player1Uuid) && Objects.equals(player2Uuid, that.player2Uuid)) ||
                (Objects.equals(player1Uuid, that.player2Uuid) && Objects.equals(player2Uuid, that.player1Uuid));
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Math.min(player1Uuid.hashCode(), player2Uuid.hashCode()),
                Math.max(player1Uuid.hashCode(), player2Uuid.hashCode())
        );
    }
}
