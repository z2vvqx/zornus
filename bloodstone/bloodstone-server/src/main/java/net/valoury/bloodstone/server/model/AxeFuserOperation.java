package net.valoury.bloodstone.server.model;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AxeFuserOperation(
        UUID operationId,
        UUID playerId,
        byte[] originalAxesPayload,
        int bloodAlloyCost,
        byte @Nullable [] fusedAxePayload,
        RecoverableOperationState state,
        Instant createdAt
) {

    public static final int RESERVED_ITEM_COUNT = 3;

    public AxeFuserOperation {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(
                originalAxesPayload,
                "Original axes payload cannot be null"
        );
        Objects.requireNonNull(state, "Operation state cannot be null");
        Objects.requireNonNull(createdAt, "Creation time cannot be null");
        if (bloodAlloyCost < 1) {
            throw new IllegalArgumentException(
                    "Axe Fuser Blood Alloy cost must be positive"
            );
        }
        if (state == RecoverableOperationState.READY && fusedAxePayload == null) {
            throw new IllegalArgumentException(
                    "A ready Axe Fuser operation requires a fused axe"
            );
        }
        originalAxesPayload = originalAxesPayload.clone();
        fusedAxePayload = copy(fusedAxePayload);
    }

    @Override
    public byte[] originalAxesPayload() {
        return originalAxesPayload.clone();
    }

    @Override
    public byte @Nullable [] fusedAxePayload() {
        return copy(fusedAxePayload);
    }

    public static UUID reservedItemMarker(UUID operationId, int itemIndex) {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        if (itemIndex < 0 || itemIndex >= RESERVED_ITEM_COUNT) {
            throw new IllegalArgumentException(
                    "Axe Fuser reserved item index must be from 0 to "
                            + (RESERVED_ITEM_COUNT - 1)
            );
        }
        String markerSource =
                operationId + ":axe-fuser-reserved-item:" + itemIndex;
        return UUID.nameUUIDFromBytes(
                markerSource.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static byte @Nullable [] copy(byte @Nullable [] value) {
        return value == null ? null : value.clone();
    }
}
