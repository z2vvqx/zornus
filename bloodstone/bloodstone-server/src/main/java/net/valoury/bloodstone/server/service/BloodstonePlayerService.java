package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.AxeFuserOperation;
import net.valoury.bloodstone.server.model.EnchanterOperation;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.model.RecoverableOperationState;
import net.valoury.bloodstone.server.model.RepairOperation;
import net.valoury.bloodstone.server.model.SoulboundRecovery;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstonePlayerService {

    private final BloodstoneStorage storage;
    private final BloodstoneItemService itemService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMessageService messageService;
    private final Logger logger;

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> connectionGenerations = new ConcurrentHashMap<>();
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();

    public BloodstonePlayerService(
            BloodstoneStorage storage,
            BloodstoneItemService itemService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstonePresentationService presentationService,
            BloodstoneMessageService messageService,
            Logger logger
    ) {
        this.storage = storage;
        this.itemService = itemService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.presentationService = presentationService;
        this.messageService = messageService;
        this.logger = logger;
    }

    public CompletableFuture<Void> handleJoin(Player player) {
        UUID playerId = player.getUniqueId();
        UUID generation = UUID.randomUUID();
        connectionGenerations.put(playerId, generation);
        loadingPlayers.add(playerId);

        CompletableFuture<Void> load = storage.loadPlayer(playerId, player.getName())
                .thenComposeAsync(playerData -> {
                    if (!isCurrent(player, generation)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    profiles.put(playerId, playerData.profile());
                    return recoverPendingItems(player, generation)
                            .thenCompose(ignored ->
                                    storage.fetchAxeFuserRecoveries(playerId))
                            .thenAcceptAsync(
                                    pendingAxeFuserOperations ->
                                            removeOrphanedOperationMarkers(
                                                    player,
                                                    pendingAxeFuserOperations
                                            ),
                                    mainThreadExecutor
                            );
                }, mainThreadExecutor)
                .whenComplete((ignored, exception) -> finishLoad(player, generation, exception));
        return load;
    }

    public CompletableFuture<Void> recoverRandomBoxOperation(Player player, UUID operationId) {
        UUID generation = currentGeneration(player);
        return storage.fetchRandomBoxRecoveries(player.getUniqueId())
                .thenComposeAsync(recoveries -> recoverRandomBoxItems(
                        player,
                        generation,
                        recoveries.stream()
                                .filter(recovery -> recovery.operationId().equals(operationId))
                                .toList()
                ), mainThreadExecutor);
    }

    public CompletableFuture<Void> recoverEnchanterOperation(Player player, UUID operationId) {
        UUID generation = currentGeneration(player);
        return storage.fetchEnchanterRecoveries(player.getUniqueId())
                .thenComposeAsync(recoveries -> recoverEnchanterItems(
                        player,
                        generation,
                        recoveries.stream()
                                .filter(recovery -> recovery.operationId().equals(operationId))
                                .toList()
                ), mainThreadExecutor);
    }

    public CompletableFuture<Void> recoverRepairOperation(Player player, UUID operationId) {
        UUID generation = currentGeneration(player);
        return storage.fetchRepairRecoveries(player.getUniqueId())
                .thenComposeAsync(recoveries -> recoverRepairItems(
                        player,
                        generation,
                        recoveries.stream()
                                .filter(recovery -> recovery.operationId().equals(operationId))
                                .toList()
                ), mainThreadExecutor);
    }

    public CompletableFuture<Void> recoverAxeFuserOperation(
            Player player,
            UUID operationId
    ) {
        UUID generation = currentGeneration(player);
        return storage.fetchAxeFuserRecoveries(player.getUniqueId())
                .thenComposeAsync(recoveries -> recoverAxeFuserItems(
                        player,
                        generation,
                        recoveries.stream()
                                .filter(recovery ->
                                        recovery.operationId().equals(operationId))
                                .toList()
                ), mainThreadExecutor);
    }

    public CompletableFuture<Void> handleQuit(Player player) {
        UUID playerId = player.getUniqueId();
        connectionGenerations.remove(playerId);
        loadingPlayers.remove(playerId);
        profiles.remove(playerId);
        return CompletableFuture.completedFuture(null);
    }

    public void refreshProfiles(Collection<UUID> playerIds) {
        for (UUID playerId : Set.copyOf(playerIds)) {
            Player onlinePlayer = Bukkit.getPlayer(playerId);
            PlayerProfile existingProfile = profiles.get(playerId);
            String username = onlinePlayer != null
                    ? onlinePlayer.getName()
                    : existingProfile != null
                    ? existingProfile.username()
                    : playerId.toString().substring(0, 16);
            storage.loadPlayer(playerId, username)
                    .thenAcceptAsync(playerData ->
                            profiles.put(playerId, playerData.profile()), mainThreadExecutor)
                    .exceptionally(exception -> {
                        logger.log(Level.WARNING,
                                "Failed to refresh Bloodstone profile " + playerId,
                                exception);
                        return null;
                    });
        }
    }

    public Optional<PlayerProfile> profile(UUID playerId) {
        return Optional.ofNullable(profiles.get(playerId));
    }

    public boolean isLoaded(UUID playerId) {
        return profiles.containsKey(playerId) && !loadingPlayers.contains(playerId);
    }

    public CompletableFuture<DeliveryOutcome> deliverReservedItem(
            Player player,
            UUID operationId,
            ItemStack item,
            boolean soulbound,
            Supplier<CompletableFuture<Boolean>> completion
    ) {
        UUID generation = connectionGenerations.get(player.getUniqueId());
        if (generation == null) {
            return CompletableFuture.completedFuture(DeliveryOutcome.PLAYER_OFFLINE);
        }

        return CompletableFuture.supplyAsync(() -> {
            if (!isCurrent(player, generation)) {
                return DeliveryAttempt.playerOffline();
            }
            if (hasOperationItem(player, operationId)) {
                return DeliveryAttempt.present();
            }

            ItemStack recoverableItem = itemService.withOperationId(item, operationId);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(recoverableItem);
            if (leftovers.isEmpty()) {
                return DeliveryAttempt.added();
            }
            if (soulbound) {
                removeOperationItems(player, operationId);
                return DeliveryAttempt.inventoryFull();
            }
            List<Item> droppedItems = new ArrayList<>();
            for (ItemStack leftover : leftovers.values()) {
                droppedItems.add(player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
            return DeliveryAttempt.dropped(droppedItems);
        }, mainThreadExecutor).thenCompose(attempt -> {
            if (attempt.type() == DeliveryAttemptType.PLAYER_OFFLINE) {
                return CompletableFuture.completedFuture(DeliveryOutcome.PLAYER_OFFLINE);
            }
            if (attempt.type() == DeliveryAttemptType.INVENTORY_FULL) {
                mainThreadExecutor.execute(() -> messageService.sendUnable(
                        player,
                        BloodstoneServerConstants
                                .RESERVED_ITEM_INVENTORY_SPACE_REQUIRED
                ));
                return CompletableFuture.completedFuture(DeliveryOutcome.INVENTORY_FULL);
            }

            CompletableFuture<DeliveryOutcome> delivery = completion.get().thenApplyAsync(completed -> {
                if (!completed) {
                    throw new IllegalStateException(
                            "Reserved item completion was rejected for operation " + operationId
                    );
                }
                if (isCurrent(player, generation)) {
                    removeOperationMarker(player, operationId);
                    if (soulbound && attempt.type() == DeliveryAttemptType.ADDED) {
                        presentationService.playSoulboundReturn(player);
                    }
                }
                for (Item droppedItem : attempt.droppedItems()) {
                    if (!droppedItem.isDead()) {
                        droppedItem.setItemStack(
                                itemService.withoutOperationId(droppedItem.getItemStack())
                        );
                    }
                }
                return switch (attempt.type()) {
                    case ADDED -> DeliveryOutcome.DELIVERED;
                    case DROPPED -> DeliveryOutcome.DROPPED;
                    case PRESENT -> DeliveryOutcome.ALREADY_PRESENT;
                    default -> throw new IllegalStateException(
                            "Unexpected delivery attempt " + attempt.type()
                    );
                };
            }, mainThreadExecutor);
            return delivery.whenComplete((ignored, exception) -> {
                if (exception != null && !attempt.droppedItems().isEmpty()) {
                    mainThreadExecutor.execute(() ->
                            attempt.droppedItems().forEach(Item::remove));
                }
            });
        });
    }

    public CompletableFuture<Void> shutdown() {
        connectionGenerations.clear();
        loadingPlayers.clear();
        profiles.clear();
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> recoverPendingItems(Player player, UUID generation) {
        UUID playerId = player.getUniqueId();
        return storage.fetchSoulboundRecoveries(playerId)
                .thenComposeAsync(recoveries ->
                                recoverSoulboundItems(player, generation, recoveries),
                        mainThreadExecutor)
                .thenCompose(ignored -> storage.fetchRandomBoxRecoveries(playerId))
                .thenComposeAsync(recoveries ->
                                recoverRandomBoxItems(player, generation, recoveries),
                        mainThreadExecutor)
                .thenCompose(ignored -> storage.fetchEnchanterRecoveries(playerId))
                .thenComposeAsync(recoveries ->
                                recoverEnchanterItems(player, generation, recoveries),
                        mainThreadExecutor)
                .thenCompose(ignored -> storage.fetchRepairRecoveries(playerId))
                .thenComposeAsync(recoveries ->
                                recoverRepairItems(player, generation, recoveries),
                        mainThreadExecutor)
                .thenCompose(ignored -> storage.fetchAxeFuserRecoveries(playerId))
                .thenComposeAsync(recoveries ->
                                recoverAxeFuserItems(player, generation, recoveries),
                        mainThreadExecutor);
    }

    private CompletableFuture<Void> recoverSoulboundItems(
            Player player,
            UUID generation,
            List<SoulboundRecovery> recoveries
    ) {
        CompletableFuture<Void> recoveryChain = CompletableFuture.completedFuture(null);
        for (SoulboundRecovery recovery : recoveries) {
            recoveryChain = recoveryChain.thenCompose(ignored -> {
                if (!isCurrent(player, generation)) {
                    return CompletableFuture.completedFuture(null);
                }
                return deserializeAndDeliver(
                        player,
                        recovery.operationId(),
                        recovery.itemPayload(),
                        true,
                        () -> storage.completeSoulboundRecovery(
                                recovery.operationId(),
                                recovery.playerId()
                        )
                );
            });
        }
        return recoveryChain;
    }

    private CompletableFuture<Void> recoverRandomBoxItems(
            Player player,
            UUID generation,
            List<RandomBoxOperation> recoveries
    ) {
        CompletableFuture<Void> recoveryChain = CompletableFuture.completedFuture(null);
        for (RandomBoxOperation recovery : recoveries) {
            recoveryChain = recoveryChain.thenCompose(ignored -> deserializeAndDeliver(
                    player,
                    recovery.operationId(),
                    recovery.rewardPayload(),
                    false,
                    () -> storage.completeRandomBox(recovery.operationId(), recovery.playerId())
            ));
        }
        return recoveryChain;
    }

    private CompletableFuture<Void> recoverEnchanterItems(
            Player player,
            UUID generation,
            List<EnchanterOperation> recoveries
    ) {
        CompletableFuture<Void> recoveryChain = CompletableFuture.completedFuture(null);
        for (EnchanterOperation recovery : recoveries) {
            recoveryChain = recoveryChain.thenCompose(ignored -> deserializeAndDeliver(
                    player,
                    recovery.operationId(),
                    recovery.recoveryPayload(),
                    false,
                    () -> storage.completeEnchanterOperation(
                            recovery.operationId(),
                            recovery.playerId()
                    )
            ));
        }
        return recoveryChain;
    }

    private CompletableFuture<Void> recoverRepairItems(
            Player player,
            UUID generation,
            List<RepairOperation> recoveries
    ) {
        CompletableFuture<Void> recoveryChain = CompletableFuture.completedFuture(null);
        for (RepairOperation recovery : recoveries) {
            recoveryChain = recoveryChain.thenCompose(ignored -> deserializeAndDeliver(
                    player,
                    recovery.operationId(),
                    recovery.recoveryPayload(),
                    false,
                    () -> storage.completeRepairOperation(
                            recovery.operationId(),
                            recovery.playerId()
                    )
            ));
        }
        return recoveryChain;
    }

    private CompletableFuture<Void> recoverAxeFuserItems(
            Player player,
            UUID generation,
            List<AxeFuserOperation> recoveries
    ) {
        CompletableFuture<Void> recoveryChain =
                CompletableFuture.completedFuture(null);
        for (AxeFuserOperation recovery : recoveries) {
            recoveryChain = recoveryChain.thenCompose(ignored -> {
                if (!isCurrent(player, generation)) {
                    return CompletableFuture.completedFuture(null);
                }
                if (recovery.state() == RecoverableOperationState.READY) {
                    return deserializeAndDeliver(
                            player,
                            recovery.operationId(),
                            recovery.fusedAxePayload(),
                            true,
                            () -> storage.completeAxeFuserOperation(
                                    recovery.operationId(),
                                    recovery.playerId()
                            )
                    );
                }
                return deserializeAndDeliverAxeFuserReservation(
                        player,
                        recovery
                );
            });
        }
        return recoveryChain;
    }

    private CompletableFuture<Void> deserializeAndDeliverAxeFuserReservation(
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
        List<ItemStack> reservedItems = new ArrayList<>(List.of(originalAxes));
        reservedItems.add(
                itemService.createBloodAlloy(recovery.bloodAlloyCost())
        );
        return deliverReservedAxeFuserItems(
                player,
                recovery.operationId(),
                List.copyOf(reservedItems),
                () -> storage.completeAxeFuserOperation(
                        recovery.operationId(),
                        recovery.playerId()
                )
        ).thenApply(ignored -> null);
    }

    private CompletableFuture<DeliveryOutcome> deliverReservedAxeFuserItems(
            Player player,
            UUID operationId,
            List<ItemStack> reservedItems,
            Supplier<CompletableFuture<Boolean>> completion
    ) {
        UUID generation = connectionGenerations.get(player.getUniqueId());
        if (generation == null) {
            return CompletableFuture.completedFuture(
                    DeliveryOutcome.PLAYER_OFFLINE
            );
        }
        return CompletableFuture.supplyAsync(() -> {
            if (!isCurrent(player, generation)) {
                return false;
            }
            int missingItems = 0;
            for (int itemIndex = 0;
                 itemIndex < reservedItems.size();
                 itemIndex++) {
                UUID marker = AxeFuserOperation.reservedItemMarker(
                        operationId,
                        itemIndex
                );
                if (!hasOperationItem(player, marker)) {
                    missingItems++;
                }
            }
            if (emptyInventorySlots(player) < missingItems) {
                messageService.sendUnable(
                        player,
                        BloodstoneServerConstants
                                .RESERVED_ITEM_INVENTORY_SPACE_REQUIRED
                );
                return false;
            }
            for (int itemIndex = 0;
                 itemIndex < reservedItems.size();
                 itemIndex++) {
                UUID marker = AxeFuserOperation.reservedItemMarker(
                        operationId,
                        itemIndex
                );
                if (!hasOperationItem(player, marker)) {
                    ItemStack recoverableItem = itemService.withOperationId(
                            reservedItems.get(itemIndex),
                            marker
                    );
                    if (!player.getInventory()
                            .addItem(recoverableItem)
                            .isEmpty()) {
                        throw new IllegalStateException(
                                "Reserved Axe Fuser item did not fit after "
                                        + "capacity validation"
                        );
                    }
                }
            }
            return true;
        }, mainThreadExecutor).thenCompose(delivered -> {
            if (!delivered) {
                return CompletableFuture.completedFuture(
                        isCurrent(player, generation)
                                ? DeliveryOutcome.INVENTORY_FULL
                                : DeliveryOutcome.PLAYER_OFFLINE
                );
            }
            return completion.get().thenApplyAsync(completed -> {
                if (!completed) {
                    throw new IllegalStateException(
                            "Reserved Axe Fuser input completion was rejected "
                                    + "for operation " + operationId
                    );
                }
                if (isCurrent(player, generation)) {
                    for (int itemIndex = 0;
                         itemIndex < reservedItems.size();
                         itemIndex++) {
                        removeOperationMarker(
                                player,
                                AxeFuserOperation.reservedItemMarker(
                                        operationId,
                                        itemIndex
                                )
                        );
                    }
                }
                return DeliveryOutcome.DELIVERED;
            }, mainThreadExecutor);
        });
    }

    private int emptyInventorySlots(Player player) {
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    private CompletableFuture<Void> deserializeAndDeliver(
            Player player,
            UUID operationId,
            byte[] payload,
            boolean soulbound,
            Supplier<CompletableFuture<Boolean>> completion
    ) {
        ItemStack item;
        try {
            item = BukkitItemSerialization.deserializeItem(payload);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return deliverReservedItem(player, operationId, item, soulbound, completion)
                .thenApply(ignored -> null);
    }

    private boolean hasOperationItem(Player player, UUID operationId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null
                    && item.getType() != Material.AIR
                    && itemService.operationId(item).filter(operationId::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private void removeOperationMarker(Player player, UUID operationId) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null
                    || item.getType() == Material.AIR
                    || itemService.operationId(item).filter(operationId::equals).isEmpty()) {
                continue;
            }
            player.getInventory().setItem(slot, itemService.withoutOperationId(item));
        }
    }

    private void removeOperationItems(Player player, UUID operationId) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null
                    && item.getType() != Material.AIR
                    && itemService.operationId(item).filter(operationId::equals).isPresent()) {
                player.getInventory().clear(slot);
            }
        }
    }

    private void removeOrphanedOperationMarkers(
            Player player,
            List<AxeFuserOperation> pendingAxeFuserOperations
    ) {
        Set<UUID> protectedMarkers = new java.util.HashSet<>();
        for (AxeFuserOperation operation : pendingAxeFuserOperations) {
            protectedMarkers.add(operation.operationId());
            for (int itemIndex = 0;
                 itemIndex < AxeFuserOperation.RESERVED_ITEM_COUNT;
                 itemIndex++) {
                protectedMarkers.add(
                        AxeFuserOperation.reservedItemMarker(
                                operation.operationId(),
                                itemIndex
                        )
                );
            }
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            Optional<UUID> marker = itemService.operationId(item);
            if (item != null
                    && item.getType() != Material.AIR
                    && marker.isPresent()
                    && !protectedMarkers.contains(marker.get())) {
                player.getInventory().setItem(slot, itemService.withoutOperationId(item));
            }
        }
    }

    private void finishLoad(Player player, UUID generation, @Nullable Throwable exception) {
        mainThreadExecutor.execute(() -> {
            UUID playerId = player.getUniqueId();
            if (!isCurrent(player, generation)) {
                return;
            }
            loadingPlayers.remove(playerId);
            if (exception != null) {
                profiles.remove(playerId);
                logger.log(Level.SEVERE, "Failed to load Bloodstone player " + playerId, exception);
                player.kick(BloodstoneText.deserialize(
                        BloodstoneServerConstants.PLAYER_DATA_LOAD_FAILED_KICK
                ));
            }
        });
    }

    private boolean isCurrent(Player player, UUID generation) {
        return player.isOnline()
                && generation.equals(connectionGenerations.get(player.getUniqueId()));
    }

    private UUID currentGeneration(Player player) {
        return connectionGenerations.computeIfAbsent(
                player.getUniqueId(),
                ignored -> UUID.randomUUID()
        );
    }

    public enum DeliveryOutcome {
        DELIVERED,
        DROPPED,
        ALREADY_PRESENT,
        INVENTORY_FULL,
        PLAYER_OFFLINE;

        public boolean wasDelivered() {
            return this == DELIVERED || this == DROPPED;
        }
    }

    private enum DeliveryAttemptType {
        ADDED,
        DROPPED,
        PRESENT,
        INVENTORY_FULL,
        PLAYER_OFFLINE
    }

    private record DeliveryAttempt(DeliveryAttemptType type, List<Item> droppedItems) {

        private static DeliveryAttempt added() {
            return new DeliveryAttempt(DeliveryAttemptType.ADDED, List.of());
        }

        private static DeliveryAttempt dropped(List<Item> droppedItems) {
            return new DeliveryAttempt(DeliveryAttemptType.DROPPED, List.copyOf(droppedItems));
        }

        private static DeliveryAttempt present() {
            return new DeliveryAttempt(DeliveryAttemptType.PRESENT, List.of());
        }

        private static DeliveryAttempt inventoryFull() {
            return new DeliveryAttempt(DeliveryAttemptType.INVENTORY_FULL, List.of());
        }

        private static DeliveryAttempt playerOffline() {
            return new DeliveryAttempt(DeliveryAttemptType.PLAYER_OFFLINE, List.of());
        }
    }
}
