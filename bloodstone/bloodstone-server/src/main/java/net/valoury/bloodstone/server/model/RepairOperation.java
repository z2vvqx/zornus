package net.valoury.bloodstone.server.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RepairOperation(
        UUID operationId,
        UUID playerId,
        byte[] originalItemPayload,
        byte @Nullable [] repairedItemPayload,
        RecoverableOperationState state,
        Instant createdAt
) {
    public RepairOperation {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(originalItemPayload, "Original item payload cannot be null");
        Objects.requireNonNull(state, "Operation state cannot be null");
        Objects.requireNonNull(createdAt, "Creation time cannot be null");
        if (state == RecoverableOperationState.READY && repairedItemPayload == null) {
            throw new IllegalArgumentException("A ready repair operation requires a repaired item");
        }
        originalItemPayload = originalItemPayload.clone();
        repairedItemPayload = copy(repairedItemPayload);
    }

    @Override
    public byte[] originalItemPayload() {
        return originalItemPayload.clone();
    }

    @Override
    public byte @Nullable [] repairedItemPayload() {
        return copy(repairedItemPayload);
    }

    public byte[] recoveryPayload() {
        return state == RecoverableOperationState.READY
                ? repairedItemPayload()
                : originalItemPayload();
    }

    private static byte @Nullable [] copy(byte @Nullable [] value) {
        return value == null ? null : value.clone();
    }
}
