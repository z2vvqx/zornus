package com.zornus.friends.proxy.storage;

import com.zornus.friends.proxy.model.FriendRelation;
import com.zornus.friends.proxy.model.FriendRequest;
import com.zornus.friends.proxy.model.FriendSettings;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.friends.proxy.model.PresenceState;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FriendStorage {

    // ==================== COMPOUND OPERATIONS ====================

    CompletableFuture<SendRequestOutcome> trySendFriendRequest(@NonNull UUID senderId, @NonNull UUID receiverId);

    CompletableFuture<AcceptRequestOutcome> acceptFriendRequest(@NonNull UUID accepterId, @NonNull UUID requesterId);

    // ==================== SINGLE-QUERY OPERATIONS ====================

    CompletableFuture<Void> removeFriendRequest(@NonNull UUID sender, @NonNull UUID receiver);
    CompletableFuture<List<FriendRequest>> fetchIncomingFriendRequests(@NonNull UUID receiver);
    CompletableFuture<List<FriendRequest>> fetchOutgoingFriendRequests(@NonNull UUID sender);

    CompletableFuture<Boolean> removeFriendRelation(@NonNull UUID player1, @NonNull UUID player2);
    CompletableFuture<Boolean> hasFriendRelation(@NonNull UUID player1, @NonNull UUID player2);
    CompletableFuture<List<FriendRelation>> fetchFriendRelations(@NonNull UUID playerId);

    CompletableFuture<Optional<FriendSettings>> fetchSettings(@NonNull UUID playerId);

    CompletableFuture<Void> updateAllowMessages(@NonNull UUID playerId, boolean value);
    CompletableFuture<Void> updateAllowJump(@NonNull UUID playerId, boolean value);
    CompletableFuture<Void> updateShowLastSeen(@NonNull UUID playerId, boolean value);
    CompletableFuture<Void> updateShowLocation(@NonNull UUID playerId, boolean value);
    CompletableFuture<Void> updateAllowRequests(@NonNull UUID playerId, boolean value);
    CompletableFuture<Void> updatePresenceState(@NonNull UUID playerId, @NonNull PresenceState value);

    CompletableFuture<Void> upsertPlayer(@NonNull UUID playerId, @NonNull String username);

    CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(@NonNull String username);

    CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUuid(@NonNull UUID playerId);

    CompletableFuture<Void> saveLastSeen(@NonNull UUID playerId, @NonNull Instant timestamp);
    CompletableFuture<Optional<Instant>> fetchLastSeen(@NonNull UUID playerId);

    CompletableFuture<Void> saveLastMessageSender(@NonNull UUID playerId, @NonNull UUID senderId);
    CompletableFuture<Optional<UUID>> fetchLastMessageSender(@NonNull UUID playerId);

    CompletableFuture<Optional<Instant>> fetchFriendRequestCooldown(@NonNull UUID senderId, @NonNull UUID receiverId);

    CompletableFuture<Void> cleanupExpiredFriendRequests(@NonNull Instant now, @NonNull Duration expiry);
    CompletableFuture<Void> cleanupExpiredFriendRequestCooldowns(@NonNull Instant now, @NonNull Duration expiry);
    CompletableFuture<Void> cleanupExpiredLastMessageSenders(@NonNull Instant now, @NonNull Duration expiry);

    void close();
}
