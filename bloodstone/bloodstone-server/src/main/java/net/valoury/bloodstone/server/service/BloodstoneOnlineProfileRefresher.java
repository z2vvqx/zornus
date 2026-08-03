package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.storage.BloodstonePlayerStorage;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BloodstoneOnlineProfileRefresher {

    private final BloodstonePlayerStorage storage;
    private final BloodstonePlayerSessionRegistry playerSessions;
    private final Executor resultExecutor;
    private final Logger logger;

    BloodstoneOnlineProfileRefresher(
            BloodstonePlayerStorage storage,
            BloodstonePlayerSessionRegistry playerSessions,
            Executor resultExecutor,
            Logger logger
    ) {
        this.storage = Objects.requireNonNull(storage, "Storage cannot be null");
        this.playerSessions = Objects.requireNonNull(
                playerSessions,
                "Player sessions cannot be null"
        );
        this.resultExecutor = Objects.requireNonNull(
                resultExecutor,
                "Result executor cannot be null"
        );
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
    }

    void refresh(Collection<UUID> playerIds) {
        for (UUID playerId : Set.copyOf(playerIds)) {
            Optional<UUID> generation =
                    playerSessions.currentGeneration(playerId);
            if (generation.isEmpty() || !playerSessions.isLoaded(playerId)) {
                continue;
            }
            storage.fetchPlayer(playerId)
                    .thenAcceptAsync(playerData -> {
                        if (playerData.isEmpty()) {
                            logger.warning(
                                    "Bloodstone profile disappeared while refreshing "
                                            + playerId
                            );
                            return;
                        }
                        playerSessions.updateProfileIfCurrent(
                                playerId,
                                generation.get(),
                                playerData.get().profile()
                        );
                    }, resultExecutor)
                    .exceptionally(exception -> {
                        logger.log(
                                Level.WARNING,
                                "Failed to refresh Bloodstone profile " + playerId,
                                exception
                        );
                        return null;
                    });
        }
    }
}
