package com.zornus.punishments.proxy.storage;

import com.zornus.punishments.proxy.model.Punishment;
import com.zornus.punishments.proxy.model.PunishmentType;
import com.zornus.shared.model.PlayerRecord;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentStorage {
    CompletableFuture<CreatePunishmentOutcome> createPunishment(@NonNull Punishment punishment);
    CompletableFuture<Optional<Punishment>> revokeByIdentifier(@NonNull String identifier, UUID revokerId,
                                                                @NonNull String reason, @NonNull Instant revokedAt);
    CompletableFuture<Optional<Punishment>> revokeActive(@NonNull UUID playerId, @NonNull PunishmentType type,
                                                         UUID revokerId, @NonNull String reason,
                                                         @NonNull Instant revokedAt);
    CompletableFuture<Optional<Punishment>> fetchByIdentifier(@NonNull String identifier);
    CompletableFuture<Optional<Punishment>> fetchActive(@NonNull UUID playerId, @NonNull PunishmentType type);
    CompletableFuture<List<Punishment>> fetchHistory(@NonNull UUID playerId);
    CompletableFuture<Integer> fetchNextPresetApplicationNumber(@NonNull UUID playerId,
                                                                 @NonNull String presetName);
    CompletableFuture<List<Punishment>> claimPendingNotifications(@NonNull UUID playerId,
                                                                  @NonNull Instant now);
    CompletableFuture<Void> markNotificationDelivered(@NonNull String identifier);
    CompletableFuture<Void> expirePunishments(@NonNull Instant now);
    CompletableFuture<Void> upsertPlayer(@NonNull UUID playerId, @NonNull String username);
    CompletableFuture<Optional<PlayerRecord>> fetchPlayer(@NonNull UUID playerId);
    CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(@NonNull String username);
    void close();
}
