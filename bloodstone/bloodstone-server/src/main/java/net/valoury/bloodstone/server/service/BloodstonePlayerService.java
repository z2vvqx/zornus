package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.storage.BloodstonePlayerStorage;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstonePlayerService {

    private final BloodstonePlayerStorage storage;
    private final BloodstonePlayerSessionRegistry playerSessions;
    private final BloodstoneOperationRecoveryService operationRecoveryService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstoneOnlineProfileRefresher profileRefresher;
    private final Logger logger;

    public BloodstonePlayerService(
            BloodstonePlayerStorage storage,
            BloodstonePlayerSessionRegistry playerSessions,
            BloodstoneOperationRecoveryService operationRecoveryService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            Logger logger
    ) {
        this.storage = Objects.requireNonNull(storage, "Storage cannot be null");
        this.playerSessions = Objects.requireNonNull(
                playerSessions,
                "Player sessions cannot be null"
        );
        this.operationRecoveryService = Objects.requireNonNull(
                operationRecoveryService,
                "Operation recovery service cannot be null"
        );
        this.mainThreadExecutor = Objects.requireNonNull(
                mainThreadExecutor,
                "Main thread executor cannot be null"
        );
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
        this.profileRefresher = new BloodstoneOnlineProfileRefresher(
                storage,
                playerSessions,
                mainThreadExecutor,
                logger
        );
    }

    public CompletableFuture<Void> handleJoin(Player player) {
        UUID playerId = player.getUniqueId();
        UUID sessionGeneration = playerSessions.beginLoading(playerId);

        return storage.loadOrCreatePlayer(playerId, player.getName())
                .thenComposeAsync(playerData -> {
                    if (!isCurrent(player, sessionGeneration)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    playerSessions.storeLoadedProfile(
                            playerId,
                            sessionGeneration,
                            playerData.profile()
                    );
                    return operationRecoveryService.recoverOnJoin(
                            player,
                            sessionGeneration
                    );
                }, mainThreadExecutor)
                .whenComplete((ignored, exception) -> finishLoad(
                        player,
                        sessionGeneration,
                        exception
                ));
    }

    public CompletableFuture<Void> handleQuit(Player player) {
        playerSessions.endSession(player.getUniqueId());
        return CompletableFuture.completedFuture(null);
    }

    public void refreshOnlineProfiles(Collection<UUID> playerIds) {
        profileRefresher.refresh(playerIds);
    }

    public Optional<PlayerProfile> profile(UUID playerId) {
        return playerSessions.profile(playerId);
    }

    public boolean isLoaded(UUID playerId) {
        return playerSessions.isLoaded(playerId);
    }

    public CompletableFuture<Void> shutdown() {
        playerSessions.clear();
        return CompletableFuture.completedFuture(null);
    }

    private void finishLoad(
            Player player,
            UUID sessionGeneration,
            @Nullable Throwable exception
    ) {
        mainThreadExecutor.execute(() -> {
            UUID playerId = player.getUniqueId();
            if (!isCurrent(player, sessionGeneration)) {
                return;
            }
            playerSessions.finishLoading(
                    playerId,
                    sessionGeneration,
                    exception == null
            );
            if (exception != null) {
                logger.log(
                        Level.SEVERE,
                        "Failed to load Bloodstone player " + playerId,
                        exception
                );
                player.kick(BloodstoneText.deserialize(
                        BloodstoneServerConstants.PLAYER_DATA_LOAD_FAILED_KICK
                ));
            }
        });
    }

    private boolean isCurrent(Player player, UUID sessionGeneration) {
        return player.isOnline()
                && playerSessions.isCurrent(
                player.getUniqueId(),
                sessionGeneration
        );
    }
}
