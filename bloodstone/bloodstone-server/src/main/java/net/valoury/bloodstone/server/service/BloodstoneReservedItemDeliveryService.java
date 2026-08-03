package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.model.AxeFuserOperation;
import net.valoury.bloodstone.server.model.ReservedItemDeliveryOutcome;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class BloodstoneReservedItemDeliveryService {

    private final BloodstoneItemIdentityService itemIdentity;
    private final BloodstonePlayerSessionRegistry playerSessions;
    private final Executor mainThreadExecutor;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMessageService messageService;

    public BloodstoneReservedItemDeliveryService(
            BloodstoneItemIdentityService itemIdentity,
            BloodstonePlayerSessionRegistry playerSessions,
            Executor mainThreadExecutor,
            BloodstonePresentationService presentationService,
            BloodstoneMessageService messageService
    ) {
        this.itemIdentity = Objects.requireNonNull(
                itemIdentity,
                "Item identity cannot be null"
        );
        this.playerSessions = Objects.requireNonNull(
                playerSessions,
                "Player sessions cannot be null"
        );
        this.mainThreadExecutor = Objects.requireNonNull(
                mainThreadExecutor,
                "Main thread executor cannot be null"
        );
        this.presentationService = Objects.requireNonNull(
                presentationService,
                "Presentation service cannot be null"
        );
        this.messageService = Objects.requireNonNull(
                messageService,
                "Message service cannot be null"
        );
    }

    public CompletableFuture<ReservedItemDeliveryOutcome> deliver(
            Player player,
            UUID operationId,
            ItemStack item,
            boolean soulbound,
            Supplier<CompletableFuture<Boolean>> completion
    ) {
        Optional<UUID> sessionGeneration = playerSessions.currentGeneration(
                player.getUniqueId()
        );
        if (sessionGeneration.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ReservedItemDeliveryOutcome.PLAYER_OFFLINE
            );
        }

        return CompletableFuture.supplyAsync(
                () -> attemptDelivery(
                        player,
                        sessionGeneration.get(),
                        operationId,
                        item,
                        soulbound
                ),
                mainThreadExecutor
        ).thenCompose(attempt -> finishDelivery(
                player,
                sessionGeneration.get(),
                operationId,
                soulbound,
                attempt,
                completion
        ));
    }

    CompletableFuture<ReservedItemDeliveryOutcome> deliverAxeFuserInputs(
            Player player,
            UUID operationId,
            List<ItemStack> reservedItems,
            Supplier<CompletableFuture<Boolean>> completion
    ) {
        Optional<UUID> sessionGeneration = playerSessions.currentGeneration(
                player.getUniqueId()
        );
        if (sessionGeneration.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ReservedItemDeliveryOutcome.PLAYER_OFFLINE
            );
        }
        return CompletableFuture.supplyAsync(
                () -> addAxeFuserInputs(
                        player,
                        sessionGeneration.get(),
                        operationId,
                        reservedItems
                ),
                mainThreadExecutor
        ).thenCompose(delivered -> {
            if (!delivered) {
                return CompletableFuture.completedFuture(
                        isCurrent(player, sessionGeneration.get())
                                ? ReservedItemDeliveryOutcome.INVENTORY_FULL
                                : ReservedItemDeliveryOutcome.PLAYER_OFFLINE
                );
            }
            return completion.get().thenApplyAsync(completed -> {
                if (!completed) {
                    throw new IllegalStateException(
                            "Reserved Axe Fuser input completion was rejected "
                                    + "for operation " + operationId
                    );
                }
                if (isCurrent(player, sessionGeneration.get())) {
                    clearAxeFuserInputMarkers(
                            player,
                            operationId,
                            reservedItems.size()
                    );
                }
                return ReservedItemDeliveryOutcome.DELIVERED;
            }, mainThreadExecutor);
        });
    }

    void removeOrphanedOperationMarkers(
            Player player,
            UUID sessionGeneration,
            List<AxeFuserOperation> pendingOperations
    ) {
        if (!isCurrent(player, sessionGeneration)) {
            return;
        }
        Set<UUID> protectedMarkers = new HashSet<>();
        for (AxeFuserOperation operation : pendingOperations) {
            protectedMarkers.add(operation.operationId());
            for (int itemIndex = 0;
                 itemIndex < AxeFuserOperation.RESERVED_ITEM_COUNT;
                 itemIndex++) {
                protectedMarkers.add(AxeFuserOperation.reservedItemMarker(
                        operation.operationId(),
                        itemIndex
                ));
            }
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            Optional<UUID> marker = itemIdentity.operationId(item);
            if (marker.isPresent() && !protectedMarkers.contains(marker.get())) {
                player.getInventory().setItem(
                        slot,
                        itemIdentity.withoutOperationId(item)
                );
            }
        }
    }

    private DeliveryAttempt attemptDelivery(
            Player player,
            UUID sessionGeneration,
            UUID operationId,
            ItemStack item,
            boolean soulbound
    ) {
        if (!isCurrent(player, sessionGeneration)) {
            return DeliveryAttempt.playerOffline();
        }
        if (hasOperationItem(player, operationId)) {
            return DeliveryAttempt.present();
        }

        ItemStack recoverableItem = itemIdentity.withOperationId(
                item,
                operationId
        );
        Map<Integer, ItemStack> leftovers = player.getInventory()
                .addItem(recoverableItem);
        if (leftovers.isEmpty()) {
            return DeliveryAttempt.added();
        }
        if (soulbound) {
            removeOperationItems(player, operationId);
            return DeliveryAttempt.inventoryFull();
        }
        List<Item> droppedItems = new ArrayList<>();
        for (ItemStack leftover : leftovers.values()) {
            droppedItems.add(player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    leftover
            ));
        }
        return DeliveryAttempt.dropped(droppedItems);
    }

    private CompletableFuture<ReservedItemDeliveryOutcome> finishDelivery(
            Player player,
            UUID sessionGeneration,
            UUID operationId,
            boolean soulbound,
            DeliveryAttempt attempt,
            Supplier<CompletableFuture<Boolean>> completion
    ) {
        if (attempt.type() == DeliveryAttemptType.PLAYER_OFFLINE) {
            return CompletableFuture.completedFuture(
                    ReservedItemDeliveryOutcome.PLAYER_OFFLINE
            );
        }
        if (attempt.type() == DeliveryAttemptType.INVENTORY_FULL) {
            mainThreadExecutor.execute(() -> messageService.sendUnable(
                    player,
                    BloodstoneServerConstants
                            .RESERVED_ITEM_INVENTORY_SPACE_REQUIRED
            ));
            return CompletableFuture.completedFuture(
                    ReservedItemDeliveryOutcome.INVENTORY_FULL
            );
        }

        CompletableFuture<ReservedItemDeliveryOutcome> delivery =
                completion.get().thenApplyAsync(completed -> {
                    if (!completed) {
                        throw new IllegalStateException(
                                "Reserved item completion was rejected for operation "
                                        + operationId
                        );
                    }
                    if (isCurrent(player, sessionGeneration)) {
                        removeOperationMarker(player, operationId);
                        if (soulbound
                                && attempt.type() == DeliveryAttemptType.ADDED) {
                            presentationService.playSoulboundReturn(player);
                        }
                    }
                    clearDroppedItemMarkers(attempt.droppedItems());
                    return switch (attempt.type()) {
                        case ADDED -> ReservedItemDeliveryOutcome.DELIVERED;
                        case DROPPED -> ReservedItemDeliveryOutcome.DROPPED;
                        case PRESENT ->
                                ReservedItemDeliveryOutcome.ALREADY_PRESENT;
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
    }

    private boolean addAxeFuserInputs(
            Player player,
            UUID sessionGeneration,
            UUID operationId,
            List<ItemStack> reservedItems
    ) {
        if (!isCurrent(player, sessionGeneration)) {
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
            if (hasOperationItem(player, marker)) {
                continue;
            }
            ItemStack recoverableItem = itemIdentity.withOperationId(
                    reservedItems.get(itemIndex),
                    marker
            );
            if (!player.getInventory().addItem(recoverableItem).isEmpty()) {
                throw new IllegalStateException(
                        "Reserved Axe Fuser item did not fit after "
                                + "capacity validation"
                );
            }
        }
        return true;
    }

    private void clearAxeFuserInputMarkers(
            Player player,
            UUID operationId,
            int itemCount
    ) {
        for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
            removeOperationMarker(
                    player,
                    AxeFuserOperation.reservedItemMarker(
                            operationId,
                            itemIndex
                    )
            );
        }
    }

    private void clearDroppedItemMarkers(List<Item> droppedItems) {
        for (Item droppedItem : droppedItems) {
            if (!droppedItem.isDead()) {
                droppedItem.setItemStack(itemIdentity.withoutOperationId(
                        droppedItem.getItemStack()
                ));
            }
        }
    }

    private boolean hasOperationItem(Player player, UUID operationId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null
                    && item.getType() != Material.AIR
                    && itemIdentity.operationId(item)
                    .filter(operationId::equals)
                    .isPresent()) {
                return true;
            }
        }
        return false;
    }

    private void removeOperationMarker(Player player, UUID operationId) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (itemIdentity.operationId(item)
                    .filter(operationId::equals)
                    .isPresent()) {
                player.getInventory().setItem(
                        slot,
                        itemIdentity.withoutOperationId(item)
                );
            }
        }
    }

    private void removeOperationItems(Player player, UUID operationId) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null
                    && item.getType() != Material.AIR
                    && itemIdentity.operationId(item)
                    .filter(operationId::equals)
                    .isPresent()) {
                player.getInventory().clear(slot);
            }
        }
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

    private boolean isCurrent(Player player, UUID sessionGeneration) {
        return player.isOnline()
                && playerSessions.isCurrent(
                player.getUniqueId(),
                sessionGeneration
        );
    }

    private enum DeliveryAttemptType {
        ADDED,
        DROPPED,
        PRESENT,
        INVENTORY_FULL,
        PLAYER_OFFLINE
    }

    private record DeliveryAttempt(
            DeliveryAttemptType type,
            List<Item> droppedItems
    ) {

        private static DeliveryAttempt added() {
            return new DeliveryAttempt(
                    DeliveryAttemptType.ADDED,
                    List.of()
            );
        }

        private static DeliveryAttempt dropped(List<Item> droppedItems) {
            return new DeliveryAttempt(
                    DeliveryAttemptType.DROPPED,
                    List.copyOf(droppedItems)
            );
        }

        private static DeliveryAttempt present() {
            return new DeliveryAttempt(
                    DeliveryAttemptType.PRESENT,
                    List.of()
            );
        }

        private static DeliveryAttempt inventoryFull() {
            return new DeliveryAttempt(
                    DeliveryAttemptType.INVENTORY_FULL,
                    List.of()
            );
        }

        private static DeliveryAttempt playerOffline() {
            return new DeliveryAttempt(
                    DeliveryAttemptType.PLAYER_OFFLINE,
                    List.of()
            );
        }
    }
}
