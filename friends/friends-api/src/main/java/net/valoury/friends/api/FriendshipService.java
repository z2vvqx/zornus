package net.valoury.friends.api;

import org.jspecify.annotations.NonNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provides read-only access to friendship relationships.
 */
@FunctionalInterface
public interface FriendshipService {

    /**
     * Checks whether two players have an accepted friendship.
     *
     * @param firstPlayerId  first player
     * @param secondPlayerId second player
     * @return future containing whether the players are friends
     */
    @NonNull CompletableFuture<Boolean> areFriends(
            @NonNull UUID firstPlayerId,
            @NonNull UUID secondPlayerId
    );
}
