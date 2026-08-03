package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.AxeFuserOperation;
import net.valoury.bloodstone.server.model.EnchanterOperation;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.model.RepairOperation;
import net.valoury.bloodstone.server.model.SoulboundRecovery;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BloodstoneOperationStorage {

    CompletableFuture<SoulboundRecovery> reserveSoulboundRecovery(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] itemPayload,
            @NonNull Instant now
    );

    CompletableFuture<List<SoulboundRecovery>> fetchSoulboundRecoveries(
            @NonNull UUID playerId
    );

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

    CompletableFuture<List<RandomBoxOperation>> fetchRandomBoxRecoveries(
            @NonNull UUID playerId
    );

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

    CompletableFuture<List<EnchanterOperation>> fetchEnchanterRecoveries(
            @NonNull UUID playerId
    );

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

    CompletableFuture<List<RepairOperation>> fetchRepairRecoveries(
            @NonNull UUID playerId
    );

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
}
