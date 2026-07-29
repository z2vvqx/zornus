package net.valoury.bloodstone.server.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SoulboundRecovery(UUID operationId, UUID playerId, byte[] itemPayload, Instant createdAt) {
    public SoulboundRecovery {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(itemPayload, "Item payload cannot be null");
        Objects.requireNonNull(createdAt, "Creation time cannot be null");
        itemPayload = itemPayload.clone();
    }

    @Override
    public byte[] itemPayload() {
        return itemPayload.clone();
    }
}
