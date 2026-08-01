package net.valoury.bloodstone.server.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PlayerOperationCapacity {

    private final int maximumConcurrentOperationsPerPlayer;
    private final Map<UUID, UUID> playerIdsByOperationId = new HashMap<>();
    private final Map<UUID, Set<UUID>> operationIdsByPlayerId = new HashMap<>();

    public PlayerOperationCapacity(int maximumConcurrentOperationsPerPlayer) {
        if (maximumConcurrentOperationsPerPlayer < 1) {
            throw new IllegalArgumentException(
                    "Maximum concurrent operations per player must be positive"
            );
        }
        this.maximumConcurrentOperationsPerPlayer =
                maximumConcurrentOperationsPerPlayer;
    }

    boolean hasAvailability(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Set<UUID> activeOperationIds = operationIdsByPlayerId.get(playerId);
        return activeOperationIds == null
                || activeOperationIds.size() < maximumConcurrentOperationsPerPlayer;
    }

    boolean tryBegin(UUID playerId, UUID operationId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operationId, "operationId");
        if (playerIdsByOperationId.containsKey(operationId)
                || !hasAvailability(playerId)) {
            return false;
        }
        operationIdsByPlayerId
                .computeIfAbsent(playerId, ignored -> new HashSet<>())
                .add(operationId);
        playerIdsByOperationId.put(operationId, playerId);
        return true;
    }

    void finish(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        UUID playerId = playerIdsByOperationId.remove(operationId);
        if (playerId == null) {
            return;
        }
        Set<UUID> activeOperationIds = operationIdsByPlayerId.get(playerId);
        if (activeOperationIds == null) {
            return;
        }
        activeOperationIds.remove(operationId);
        if (activeOperationIds.isEmpty()) {
            operationIdsByPlayerId.remove(playerId);
        }
    }

    public void clear() {
        playerIdsByOperationId.clear();
        operationIdsByPlayerId.clear();
    }
}
