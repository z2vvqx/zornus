package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.StorageSession;
import net.valoury.bloodstone.server.model.StorageType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BloodstoneInventoryStorage {

    CompletableFuture<ExtraStorageUnlockOutcome> unlockExtraStorage(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            @NonNull Instant now
    );

    CompletableFuture<StorageOpenOutcome> openStorage(
            @NonNull UUID playerId,
            @NonNull StorageType storageType,
            @NonNull UUID sessionToken,
            @NonNull Instant now,
            @NonNull Duration leaseDuration
    );

    CompletableFuture<StorageWriteOutcome> checkpointStorage(
            @NonNull StorageSession session,
            byte @Nullable [] contentsPayload,
            @NonNull Instant now,
            @NonNull Duration leaseDuration
    );

    CompletableFuture<StorageWriteOutcome> closeStorage(
            @NonNull StorageSession session,
            byte @Nullable [] contentsPayload,
            @NonNull Instant now
    );
}
