package net.valoury.bloodstone.server.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EnchanterOperation(
        UUID operationId,
        UUID playerId,
        byte[] originalItemPayload,
        byte @Nullable [] enchantedItemPayload,
        RecoverableOperationState state,
        Instant createdAt
) {
    public EnchanterOperation {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(originalItemPayload, "Original item payload cannot be null");
        Objects.requireNonNull(state, "Operation state cannot be null");
        Objects.requireNonNull(createdAt, "Creation time cannot be null");
        if (state == RecoverableOperationState.READY && enchantedItemPayload == null) {
            throw new IllegalArgumentException("A ready enchanter operation requires an enchanted item");
        }
        originalItemPayload = originalItemPayload.clone();
        enchantedItemPayload = copy(enchantedItemPayload);
    }

    @Override
    public byte[] originalItemPayload() {
        return originalItemPayload.clone();
    }

    @Override
    public byte @Nullable [] enchantedItemPayload() {
        return copy(enchantedItemPayload);
    }

    public byte[] recoveryPayload() {
        return state == RecoverableOperationState.READY
                ? enchantedItemPayload()
                : originalItemPayload();
    }

    private static byte @Nullable [] copy(byte @Nullable [] value) {
        return value == null ? null : value.clone();
    }
}
