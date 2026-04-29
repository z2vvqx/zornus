package com.zornus.friends.proxy.storage;

import com.zornus.friends.proxy.model.FriendRelation;
import com.zornus.friends.proxy.model.FriendRequest;
import com.zornus.friends.proxy.model.FriendSettings;
import com.zornus.friends.proxy.model.PlayerRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FriendStorage {

    CompletableFuture<Boolean> addFriendRequest(FriendRequest request);
    CompletableFuture<Boolean> removeFriendRequest(UUID sender, UUID receiver);
    CompletableFuture<Optional<FriendRequest>> fetchFriendRequest(UUID sender, UUID receiver);
    CompletableFuture<List<FriendRequest>> fetchIncomingFriendRequests(UUID receiver);
    CompletableFuture<List<FriendRequest>> fetchOutgoingFriendRequests(UUID sender);
    CompletableFuture<Integer> countIncomingFriendRequests(UUID receiver);
    CompletableFuture<Integer> countOutgoingFriendRequests(UUID sender);
    CompletableFuture<Boolean> hasIncomingFriendRequest(UUID receiver, UUID sender);
    CompletableFuture<Boolean> hasOutgoingFriendRequest(UUID sender, UUID receiver);

    CompletableFuture<Boolean> addFriendRelation(UUID player1, UUID player2);
    CompletableFuture<Boolean> removeFriendRelation(UUID player1, UUID player2);
    CompletableFuture<Boolean> hasFriendRelation(UUID player1, UUID player2);
    CompletableFuture<List<FriendRelation>> fetchFriendRelations(UUID playerId);
    CompletableFuture<Integer> fetchFriendRelationCount(UUID playerId);

    CompletableFuture<Optional<FriendSettings>> fetchSettings(UUID playerId);
    CompletableFuture<Void> saveSettings(UUID playerId, FriendSettings settings);

    CompletableFuture<Void> upsertPlayer(UUID playerId, String username);

    CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(String username);

    CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUuid(UUID playerId);

    CompletableFuture<Void> saveLastSeen(UUID playerId, Instant timestamp);
    CompletableFuture<Optional<Instant>> fetchLastSeen(UUID playerId);

    CompletableFuture<Void> saveLastMessageSender(UUID playerId, UUID senderId);
    CompletableFuture<Optional<UUID>> fetchLastMessageSender(UUID playerId);

    CompletableFuture<Optional<Instant>> fetchFriendRequestTimestamp(UUID senderId, UUID receiverId);
    CompletableFuture<Void> saveFriendRequestTimestamp(UUID senderId, UUID receiverId);

    CompletableFuture<Void> cleanupExpiredFriendRequests(Duration expiry);
    CompletableFuture<Void> cleanupExpiredFriendRequestCooldowns(Duration expiry);
    CompletableFuture<Void> cleanupExpiredLastMessageSenders(Duration expiry);

    void close();
}
