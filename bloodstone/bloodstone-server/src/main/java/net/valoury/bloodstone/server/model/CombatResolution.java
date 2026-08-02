package net.valoury.bloodstone.server.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CombatResolution(
        UUID eventId,
        UUID killerId,
        UUID victimId,
        @Nullable UUID carryPlayerId,
        Set<UUID> assistPlayerIds,
        @Nullable UUID killerGuildId,
        @Nullable UUID victimGuildId,
        boolean domination,
        boolean revenge,
        Instant occurredAt
) {
    public CombatResolution {
        Objects.requireNonNull(eventId, "Event ID cannot be null");
        Objects.requireNonNull(killerId, "Killer ID cannot be null");
        Objects.requireNonNull(victimId, "Victim ID cannot be null");
        Objects.requireNonNull(assistPlayerIds, "Assist player IDs cannot be null");
        Objects.requireNonNull(occurredAt, "Occurrence time cannot be null");
        if (killerId.equals(victimId)) {
            throw new IllegalArgumentException("Killer and victim must differ");
        }
        assistPlayerIds = Set.copyOf(assistPlayerIds);
        if (assistPlayerIds.contains(killerId) || assistPlayerIds.contains(victimId)) {
            throw new IllegalArgumentException("Killer and victim cannot receive assists");
        }
        if (carryPlayerId != null
                && (carryPlayerId.equals(killerId) || carryPlayerId.equals(victimId))) {
            throw new IllegalArgumentException("Killer and victim cannot receive the carry");
        }
        if (carryPlayerId != null && !assistPlayerIds.contains(carryPlayerId)) {
            throw new IllegalArgumentException("Carry player must also receive an assist");
        }
    }
}
