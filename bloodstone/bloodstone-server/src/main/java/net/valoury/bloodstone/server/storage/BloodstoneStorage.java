package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.CombatResolution;
import net.valoury.bloodstone.server.model.AxeFuserOperation;
import net.valoury.bloodstone.server.model.EnchanterOperation;
import net.valoury.bloodstone.server.model.GuildLeaderboardEntry;
import net.valoury.bloodstone.server.model.LeaderboardMetric;
import net.valoury.bloodstone.server.model.PlayerData;
import net.valoury.bloodstone.server.model.PlayerLeaderboardEntry;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.model.RepairOperation;
import net.valoury.bloodstone.server.model.SoulboundRecovery;
import net.valoury.bloodstone.server.model.StorageSession;
import net.valoury.bloodstone.server.model.StorageType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BloodstoneStorage extends AutoCloseable {

    CompletableFuture<Void> initialize();

    CompletableFuture<PlayerData> loadPlayer(@NonNull UUID playerId, @NonNull String username);

    CompletableFuture<ProfileSaveOutcome> savePlayerProfile(@NonNull PlayerProfile profile);

    CompletableFuture<CombatResolutionOutcome> resolveCombat(@NonNull CombatResolution resolution);

    CompletableFuture<Boolean> recordDeath(
            @NonNull UUID eventId,
            @NonNull UUID victimId,
            @Nullable UUID victimGuildId,
            @NonNull Instant occurredAt
    );

    CompletableFuture<ExtraStorageUnlockOutcome> unlockExtraStorage(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            @NonNull Instant now
    );

    CompletableFuture<SoulboundRecovery> reserveSoulboundRecovery(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] itemPayload,
            @NonNull Instant now
    );

    CompletableFuture<List<SoulboundRecovery>> fetchSoulboundRecoveries(@NonNull UUID playerId);

    CompletableFuture<Boolean> completeSoulboundRecovery(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    );

    CompletableFuture<RandomBoxReserveOutcome> reserveRandomBox(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            @NonNull String rewardId,
            byte @NonNull [] rewardPayload,
            int maximumFreeUses,
            int paidBloodCost,
            boolean paidUseAllowed,
            @NonNull Instant now
    );

    CompletableFuture<List<RandomBoxOperation>> fetchRandomBoxRecoveries(@NonNull UUID playerId);

    CompletableFuture<Boolean> completeRandomBox(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    );

    CompletableFuture<EnchanterReserveOutcome> reserveEnchanterOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            @NonNull String offerKey,
            @NonNull Instant now,
            @NonNull Duration cooldown,
            byte @NonNull [] originalItemPayload
    );

    CompletableFuture<Boolean> markEnchanterOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] enchantedItemPayload
    );

    CompletableFuture<List<EnchanterOperation>> fetchEnchanterRecoveries(@NonNull UUID playerId);

    CompletableFuture<Boolean> completeEnchanterOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    );

    CompletableFuture<RepairReserveOutcome> reserveRepairOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] originalItemPayload,
            @NonNull Instant now
    );

    CompletableFuture<Boolean> markRepairOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] repairedItemPayload
    );

    CompletableFuture<List<RepairOperation>> fetchRepairRecoveries(@NonNull UUID playerId);

    CompletableFuture<Boolean> completeRepairOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    );

    CompletableFuture<AxeFuserReserveOutcome> reserveAxeFuserOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] originalAxesPayload,
            int bloodAlloyCost,
            @NonNull Instant now
    );

    CompletableFuture<Boolean> markAxeFuserOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] fusedAxePayload
    );

    CompletableFuture<List<AxeFuserOperation>> fetchAxeFuserRecoveries(
            @NonNull UUID playerId
    );

    CompletableFuture<Boolean> completeAxeFuserOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
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

    CompletableFuture<List<PlayerLeaderboardEntry>> fetchPlayerLeaderboard(
            @NonNull LeaderboardMetric metric
    );

    CompletableFuture<List<GuildLeaderboardEntry>> fetchGuildLeaderboard(
            @NonNull LeaderboardMetric metric
    );

    @Override
    void close();
}
