package net.valoury.bloodstone.server.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RandomBoxOperation(
        UUID operationId,
        UUID playerId,
        String rewardId,
        byte[] rewardPayload,
        boolean freeUse,
        int bloodCost,
        Instant createdAt
) {
    public RandomBoxOperation {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(rewardId, "Reward ID cannot be null");
        Objects.requireNonNull(rewardPayload, "Reward payload cannot be null");
        Objects.requireNonNull(createdAt, "Creation time cannot be null");
        if (rewardId.isBlank() || bloodCost < 0) {
            throw new IllegalArgumentException("Invalid random-box reservation");
        }
        if (freeUse && bloodCost != 0) {
            throw new IllegalArgumentException("A free random-box use cannot have a blood cost");
        }
        rewardPayload = rewardPayload.clone();
    }

    @Override
    public byte[] rewardPayload() {
        return rewardPayload.clone();
    }
}
