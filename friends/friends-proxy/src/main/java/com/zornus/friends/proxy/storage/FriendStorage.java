package com.zornus.friends.proxy.storage;

import com.zornus.friends.proxy.model.FriendRelation;
import com.zornus.friends.proxy.model.FriendRequest;
import com.zornus.friends.proxy.model.FriendSettings;
import com.zornus.shared.model.PlayerRecord;
import com.zornus.friends.proxy.model.PresenceState;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FriendStorage {

    // ==================== COMPOUND OPERATIONS ====================

    CompletableFuture<SendRequestOutcome> trySendFriendRequest(UUID senderId, UUID receiverId);

    CompletableFuture<AcceptRequestOutcome> acceptFriendRequest(UUID accepterId, UUID requesterId);

    // ==================== SINGLE-QUERY OPERATIONS ====================

    CompletableFuture<Boolean> removeFriendRequest(UUID sender, UUID receiver);
    CompletableFuture<List<FriendRequest>> fetchIncomingFriendRequests(UUID receiver);
    CompletableFuture<List<FriendRequest>> fetchOutgoingFriendRequests(UUID sender);

    CompletableFuture<Boolean> removeFriendRelation(UUID player1, UUID player2);
    CompletableFuture<Boolean> hasFriendRelation(UUID player1, UUID player2);
    CompletableFuture<List<FriendRelation>> fetchFriendRelations(UUID playerId);

    CompletableFuture<Optional<FriendSettings>> fetchSettings(UUID playerId);

    CompletableFuture<Void> updateAllowMessages(UUID playerId, boolean value);
    CompletableFuture<Void> updateAllowJump(UUID playerId, boolean value);
    CompletableFuture<Void> updateShowLastSeen(UUID playerId, boolean value);
    CompletableFuture<Void> updateShowLocation(UUID playerId, boolean value);
    CompletableFuture<Void> updateAllowRequests(UUID playerId, boolean value);
    CompletableFuture<Void> updatePresenceState(UUID playerId, PresenceState value);

    CompletableFuture<Void> upsertPlayer(UUID playerId, String username);

    CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(String username);

    CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUuid(UUID playerId);

    CompletableFuture<Void> saveLastSeen(UUID playerId, Instant timestamp);
    CompletableFuture<Optional<Instant>> fetchLastSeen(UUID playerId);

    CompletableFuture<Void> saveLastMessageSender(UUID playerId, UUID senderId);
    CompletableFuture<Optional<UUID>> fetchLastMessageSender(UUID playerId);

    CompletableFuture<Void> cleanupExpiredFriendRequests(Instant now, Duration expiry);
    CompletableFuture<Void> cleanupExpiredFriendRequestCooldowns(Instant now, Duration expiry);
    CompletableFuture<Void> cleanupExpiredLastMessageSenders(Instant now, Duration expiry);

    void close();
}
