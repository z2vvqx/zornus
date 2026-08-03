package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.model.AxeFuserOperation;
import net.valoury.bloodstone.server.model.EnchanterOperation;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.model.RecoverableOperationState;
import net.valoury.bloodstone.server.model.RepairOperation;
import net.valoury.bloodstone.server.model.SoulboundRecovery;
import net.valoury.bloodstone.server.storage.BloodstoneOperationStorage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

public final class BloodstoneOperationRecoveryService {

    private final BloodstoneOperationStorage storage;
    private final BloodstoneCurrencyService currencyService;
    private final BloodstonePlayerSessionRegistry playerSessions;
    private final BloodstoneReservedItemDeliveryService deliveryService;
    private final Executor mainThreadExecutor;

    public BloodstoneOperationRecoveryService(
            BloodstoneOperationStorage storage,
            BloodstoneCurrencyService currencyService,
            BloodstonePlayerSessionRegistry playerSessions,
            BloodstoneReservedItemDeliveryService deliveryService,
            Executor mainThreadExecutor
    ) {
        this.storage = Objects.requireNonNull(storage, "Storage cannot be null");
        this.currencyService = Objects.requireNonNull(
                currencyService,
                "Currency service cannot be null"
        );
        this.playerSessions = Objects.requireNonNull(
                playerSessions,
                "Player sessions cannot be null"
        );
        this.deliveryService = Objects.requireNonNull(
                deliveryService,
                "Delivery service cannot be null"
        );
        this.mainThreadExecutor = Objects.requireNonNull(
                mainThreadExecutor,
                "Main thread executor cannot be null"
        );
    }

    public CompletableFuture<Void> recoverOnJoin(
            Player player,
            UUID sessionGeneration
    ) {
        UUID playerId = player.getUniqueId();
        return fetchIfCurrent(
                playerId,
                sessionGeneration,
                () -> storage.fetchSoulboundRecoveries(playerId)
        ).thenComposeAsync(
                recoveries -> recoverSoulboundItems(
                        player,
                        sessionGeneration,
                        recoveries
                ),
                mainThreadExecutor
        ).thenCompose(ignored -> fetchIfCurrent(
                playerId,
                sessionGeneration,
                () -> storage.fetchRandomBoxRecoveries(playerId)
        )).thenComposeAsync(
                recoveries -> recoverRandomBoxItems(
                        player,
                        sessionGeneration,
                        recoveries
                ),
                mainThreadExecutor
        ).thenCompose(ignored -> fetchIfCurrent(
                playerId,
                sessionGeneration,
                () -> storage.fetchEnchanterRecoveries(playerId)
        )).thenComposeAsync(
                recoveries -> recoverEnchanterItems(
                        player,
                        sessionGeneration,
                        recoveries
                ),
                mainThreadExecutor
        ).thenCompose(ignored -> fetchIfCurrent(
                playerId,
                sessionGeneration,
                () -> storage.fetchRepairRecoveries(playerId)
        )).thenComposeAsync(
                recoveries -> recoverRepairItems(
                        player,
                        sessionGeneration,
                        recoveries
                ),
                mainThreadExecutor
        ).thenCompose(ignored -> fetchIfCurrent(
                playerId,
                sessionGeneration,
                () -> storage.fetchAxeFuserRecoveries(playerId)
        )).thenComposeAsync(
                recoveries -> recoverAxeFuserItems(
                        player,
                        sessionGeneration,
                        recoveries
                ),
                mainThreadExecutor
        ).thenCompose(ignored -> fetchIfCurrent(
                playerId,
                sessionGeneration,
                () -> storage.fetchAxeFuserRecoveries(playerId)
        )).thenAcceptAsync(
                pendingOperations ->
                        deliveryService.removeOrphanedOperationMarkers(
                                player,
                                sessionGeneration,
                                pendingOperations
                        ),
                mainThreadExecutor
        );
    }

    public CompletableFuture<Void> recoverRandomBoxOperation(
            Player player,
            UUID operationId
    ) {
        UUID sessionGeneration = currentGeneration(player);
        return storage.fetchRandomBoxRecoveries(player.getUniqueId())
                .thenComposeAsync(
                        recoveries -> recoverRandomBoxItems(
                                player,
                                sessionGeneration,
                                matchingOperation(
                                        recoveries,
                                        operationId,
                                        RandomBoxOperation::operationId
                                )
                        ),
                        mainThreadExecutor
                );
    }

    public CompletableFuture<Void> recoverEnchanterOperation(
            Player player,
            UUID operationId
    ) {
        UUID sessionGeneration = currentGeneration(player);
        return storage.fetchEnchanterRecoveries(player.getUniqueId())
                .thenComposeAsync(
                        recoveries -> recoverEnchanterItems(
                                player,
                                sessionGeneration,
                                matchingOperation(
                                        recoveries,
                                        operationId,
                                        EnchanterOperation::operationId
                                )
                        ),
                        mainThreadExecutor
                );
    }

    public CompletableFuture<Void> recoverRepairOperation(
            Player player,
            UUID operationId
    ) {
        UUID sessionGeneration = currentGeneration(player);
        return storage.fetchRepairRecoveries(player.getUniqueId())
                .thenComposeAsync(
                        recoveries -> recoverRepairItems(
                                player,
                                sessionGeneration,
                                matchingOperation(
                                        recoveries,
                                        operationId,
                                        RepairOperation::operationId
                                )
                        ),
                        mainThreadExecutor
                );
    }

    public CompletableFuture<Void> recoverAxeFuserOperation(
            Player player,
            UUID operationId
    ) {
        UUID sessionGeneration = currentGeneration(player);
        return storage.fetchAxeFuserRecoveries(player.getUniqueId())
                .thenComposeAsync(
                        recoveries -> recoverAxeFuserItems(
                                player,
                                sessionGeneration,
                                matchingOperation(
                                        recoveries,
                                        operationId,
                                        AxeFuserOperation::operationId
                                )
                        ),
                        mainThreadExecutor
                );
    }

    private CompletableFuture<Void> recoverSoulboundItems(
            Player player,
            UUID sessionGeneration,
            List<SoulboundRecovery> recoveries
    ) {
        return recoverSerializedItems(
                player,
                sessionGeneration,
                recoveries.stream()
                        .map(recovery -> new SerializedItemRecovery(
                                recovery.operationId(),
                                recovery.itemPayload(),
                                true,
                                () -> storage.completeSoulboundRecovery(
                                        recovery.operationId(),
                                        recovery.playerId()
                                )
                        ))
                        .toList()
        );
    }

    private CompletableFuture<Void> recoverRandomBoxItems(
            Player player,
            UUID sessionGeneration,
            List<RandomBoxOperation> recoveries
    ) {
        return recoverSerializedItems(
                player,
                sessionGeneration,
                recoveries.stream()
                        .map(recovery -> new SerializedItemRecovery(
                                recovery.operationId(),
                                recovery.rewardPayload(),
                                false,
                                () -> storage.completeRandomBox(
                                        recovery.operationId(),
                                        recovery.playerId()
                                )
                        ))
                        .toList()
        );
    }

    private CompletableFuture<Void> recoverEnchanterItems(
            Player player,
            UUID sessionGeneration,
            List<EnchanterOperation> recoveries
    ) {
        return recoverSerializedItems(
                player,
                sessionGeneration,
                recoveries.stream()
                        .map(recovery -> new SerializedItemRecovery(
                                recovery.operationId(),
                                recovery.recoveryPayload(),
                                false,
                                () -> storage.completeEnchanterOperation(
                                        recovery.operationId(),
                                        recovery.playerId()
                                )
                        ))
                        .toList()
        );
    }

    private CompletableFuture<Void> recoverRepairItems(
            Player player,
            UUID sessionGeneration,
            List<RepairOperation> recoveries
    ) {
        return recoverSerializedItems(
                player,
                sessionGeneration,
                recoveries.stream()
                        .map(recovery -> new SerializedItemRecovery(
                                recovery.operationId(),
                                recovery.recoveryPayload(),
                                false,
                                () -> storage.completeRepairOperation(
                                        recovery.operationId(),
                                        recovery.playerId()
                                )
                        ))
                        .toList()
        );
    }

    private CompletableFuture<Void> recoverAxeFuserItems(
            Player player,
            UUID sessionGeneration,
            List<AxeFuserOperation> recoveries
    ) {
        CompletableFuture<Void> recoveryChain =
                CompletableFuture.completedFuture(null);
        for (AxeFuserOperation recovery : recoveries) {
            recoveryChain = recoveryChain.thenCompose(ignored -> {
                if (!isCurrent(player, sessionGeneration)) {
                    return CompletableFuture.completedFuture(null);
                }
                if (recovery.state() == RecoverableOperationState.READY) {
                    return recoverSerializedItems(
                            player,
                            sessionGeneration,
                            List.of(new SerializedItemRecovery(
                                    recovery.operationId(),
                                    recovery.fusedAxePayload(),
                                    true,
                                    () -> storage.completeAxeFuserOperation(
                                            recovery.operationId(),
                                            recovery.playerId()
                                    )
                            ))
                    );
                }
                return recoverAxeFuserInputs(player, recovery);
            });
        }
        return recoveryChain;
    }

    private CompletableFuture<Void> recoverSerializedItems(
            Player player,
            UUID sessionGeneration,
            List<SerializedItemRecovery> recoveries
    ) {
        CompletableFuture<Void> recoveryChain =
                CompletableFuture.completedFuture(null);
        for (SerializedItemRecovery recovery : recoveries) {
            recoveryChain = recoveryChain.thenCompose(ignored -> {
                if (!isCurrent(player, sessionGeneration)) {
                    return CompletableFuture.completedFuture(null);
                }
                ItemStack item;
                try {
                    item = BukkitItemSerialization.deserializeItem(
                            recovery.itemPayload()
                    );
                } catch (IOException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
                return deliveryService.deliver(
                        player,
                        recovery.operationId(),
                        item,
                        recovery.soulbound(),
                        recovery.completion()
                ).thenApply(deliveryOutcome -> null);
            });
        }
        return recoveryChain;
    }

    private CompletableFuture<Void> recoverAxeFuserInputs(
            Player player,
            AxeFuserOperation recovery
    ) {
        ItemStack[] originalAxes;
        try {
            originalAxes = BukkitItemSerialization.deserializeContents(
                    recovery.originalAxesPayload()
            );
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        if (originalAxes.length != 2
                || originalAxes[0] == null
                || originalAxes[1] == null) {
            return CompletableFuture.failedFuture(new IOException(
                    "Axe Fuser recovery did not contain exactly two axes"
            ));
        }
        List<ItemStack> reservedItems = new ArrayList<>(
                List.of(originalAxes)
        );
        reservedItems.add(currencyService.createBloodAlloy(
                recovery.bloodAlloyCost()
        ));
        return deliveryService.deliverAxeFuserInputs(
                player,
                recovery.operationId(),
                List.copyOf(reservedItems),
                () -> storage.completeAxeFuserOperation(
                        recovery.operationId(),
                        recovery.playerId()
                )
        ).thenApply(deliveryOutcome -> null);
    }

    private boolean isCurrent(Player player, UUID sessionGeneration) {
        return player.isOnline()
                && playerSessions.isCurrent(
                player.getUniqueId(),
                sessionGeneration
        );
    }

    private UUID currentGeneration(Player player) {
        return playerSessions.currentGenerationOrCreate(
                player.getUniqueId()
        );
    }

    private <T> CompletableFuture<List<T>> fetchIfCurrent(
            UUID playerId,
            UUID sessionGeneration,
            Supplier<CompletableFuture<List<T>>> fetch
    ) {
        if (!playerSessions.isCurrent(playerId, sessionGeneration)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return fetch.get();
    }

    private static <T> List<T> matchingOperation(
            List<T> operations,
            UUID operationId,
            Function<T, UUID> operationIdExtractor
    ) {
        return operations.stream()
                .filter(operation -> operationId.equals(
                        operationIdExtractor.apply(operation)
                ))
                .toList();
    }

    private record SerializedItemRecovery(
            UUID operationId,
            byte[] itemPayload,
            boolean soulbound,
            Supplier<CompletableFuture<Boolean>> completion
    ) {
    }
}
