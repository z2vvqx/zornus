package com.zornus.friends.proxy.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.time.Instant;
import java.util.UUID;

public record FriendRelation(
        @NotNull UUID player1Uuid,
        @NotNull String player1Username,
        @NotNull UUID player2Uuid,
        @NotNull String player2Username,
        @NotNull Instant friendsSince
) {
    public FriendRelation {
    }

    public FriendRelation(@NotNull UUID player1Uuid, @NotNull String player1Username,
                          @NotNull UUID player2Uuid, @NotNull String player2Username) {
        this(player1Uuid, player1Username, player2Uuid, player2Username, Instant.now());
    }

    public boolean involves(@NotNull UUID playerUuid) {
        return player1Uuid.equals(playerUuid) || player2Uuid.equals(playerUuid);
    }

    public @NotNull UUID getOtherPlayerUuid(@NotNull UUID playerUuid) {
        if (player1Uuid.equals(playerUuid)) {
            return player2Uuid;
        } else if (player2Uuid.equals(playerUuid)) {
            return player1Uuid;
        }
        throw new IllegalArgumentException("Player UUID " + playerUuid + " is not part of this relation");
    }

    public @NotNull String getOtherPlayerUsername(@NotNull UUID playerUuid) {
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
