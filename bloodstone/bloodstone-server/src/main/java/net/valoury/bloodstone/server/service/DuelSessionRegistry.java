package net.valoury.bloodstone.server.service;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class DuelSessionRegistry {

    private final Map<UUID, DuelRequest> pendingRequestsByPlayerId = new HashMap<>();
    private final Map<UUID, ActiveDuel> activeDuelsByPlayerId = new HashMap<>();

    DuelRequest createRequest(UUID challengerId, UUID challengedPlayerId) {
        DuelRequest request = new DuelRequest(
                UUID.randomUUID(),
                challengerId,
                challengedPlayerId
        );
        pendingRequestsByPlayerId.put(challengerId, request);
        pendingRequestsByPlayerId.put(challengedPlayerId, request);
        return request;
    }

    @Contract(pure = true)
    @Nullable DuelRequest requestFor(UUID playerId) {
        return pendingRequestsByPlayerId.get(playerId);
    }

    void remove(DuelRequest request) {
        pendingRequestsByPlayerId.remove(request.challengerId(), request);
        pendingRequestsByPlayerId.remove(request.challengedPlayerId(), request);
    }

    ActiveDuel createDuel(
            UUID challengerId,
            UUID challengedPlayerId,
            DuelPosition challengerReturnPosition,
            DuelPosition challengedPlayerReturnPosition
    ) {
        ActiveDuel duel = new ActiveDuel(
                UUID.randomUUID(),
                challengerId,
                challengedPlayerId,
                challengerReturnPosition,
                challengedPlayerReturnPosition,
                DuelPhase.COUNTDOWN
        );
        put(duel);
        return duel;
    }

    @Contract(pure = true)
    @Nullable ActiveDuel duelFor(UUID playerId) {
        return activeDuelsByPlayerId.get(playerId);
    }

    ActiveDuel activate(ActiveDuel duel) {
        ActiveDuel activeDuel = duel.withPhase(DuelPhase.ACTIVE);
        put(activeDuel);
        return activeDuel;
    }

    @Nullable ActiveDuel removeDuelFor(UUID playerId) {
        ActiveDuel duel = activeDuelsByPlayerId.remove(playerId);
        if (duel != null) {
            activeDuelsByPlayerId.remove(duel.opponentOf(playerId), duel);
        }
        return duel;
    }

    @Contract(pure = true)
    boolean isBusy(UUID playerId) {
        return pendingRequestsByPlayerId.containsKey(playerId)
                || activeDuelsByPlayerId.containsKey(playerId);
    }

    void clear() {
        pendingRequestsByPlayerId.clear();
        activeDuelsByPlayerId.clear();
    }

    private void put(ActiveDuel duel) {
        activeDuelsByPlayerId.put(duel.challengerId(), duel);
        activeDuelsByPlayerId.put(duel.challengedPlayerId(), duel);
    }
}

enum DuelPhase {
    COUNTDOWN,
    ACTIVE
}

record DuelRequest(
        UUID requestId,
        UUID challengerId,
        UUID challengedPlayerId
) {

    @Contract(pure = true)
    UUID opponentOf(UUID playerId) {
        if (challengerId.equals(playerId)) {
            return challengedPlayerId;
        }
        if (challengedPlayerId.equals(playerId)) {
            return challengerId;
        }
        throw new IllegalArgumentException("Player is not part of this duel request");
    }
}

record ActiveDuel(
        UUID duelId,
        UUID challengerId,
        UUID challengedPlayerId,
        DuelPosition challengerReturnPosition,
        DuelPosition challengedPlayerReturnPosition,
        DuelPhase phase
) {

    @Contract(pure = true)
    UUID opponentOf(UUID playerId) {
        if (challengerId.equals(playerId)) {
            return challengedPlayerId;
        }
        if (challengedPlayerId.equals(playerId)) {
            return challengerId;
        }
        throw new IllegalArgumentException("Player is not part of this duel");
    }

    @Contract(pure = true)
    DuelPosition returnPositionOf(UUID playerId) {
        if (challengerId.equals(playerId)) {
            return challengerReturnPosition;
        }
        if (challengedPlayerId.equals(playerId)) {
            return challengedPlayerReturnPosition;
        }
        throw new IllegalArgumentException("Player is not part of this duel");
    }

    @Contract(pure = true)
    ActiveDuel withPhase(DuelPhase newPhase) {
        return new ActiveDuel(
                duelId,
                challengerId,
                challengedPlayerId,
                challengerReturnPosition,
                challengedPlayerReturnPosition,
                newPhase
        );
    }
}
