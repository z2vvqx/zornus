package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.storage.BloodstoneOperationStorage;
import net.valoury.bloodstone.server.storage.RepairReserveOutcome;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstoneRepairService {

    private static final Set<Material> REPAIRABLE_ITEMS = Set.of(
            Material.DIAMOND_SWORD,
            Material.DIAMOND_AXE,
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
            Material.BOW
    );

    private final Plugin plugin;
    private final BloodstoneOperationStorage storage;
    private final BloodstoneItemService itemService;
    private final BloodstoneItemIdentityService itemIdentity;
    private final BloodstoneOperationRecoveryService operationRecoveryService;
    private final BloodstoneReservedItemDeliveryService deliveryService;
    private final PlayerOperationCapacity playerOperationCapacity;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstoneMessageService messageService;
    private final Logger logger;
    private final Set<Item> animationDisplays = new HashSet<>();

    private volatile boolean acceptingOperations = true;

    public BloodstoneRepairService(
            Plugin plugin,
            BloodstoneOperationStorage storage,
            BloodstoneItemService itemService,
            BloodstoneItemIdentityService itemIdentity,
            BloodstoneOperationRecoveryService operationRecoveryService,
            BloodstoneReservedItemDeliveryService deliveryService,
            PlayerOperationCapacity playerOperationCapacity,
            BloodstonePresentationService presentationService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstoneMessageService messageService,
            Logger logger
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.itemService = itemService;
        this.itemIdentity = itemIdentity;
        this.operationRecoveryService = operationRecoveryService;
        this.deliveryService = deliveryService;
        this.playerOperationCapacity = playerOperationCapacity;
        this.presentationService = presentationService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.messageService = messageService;
        this.logger = logger;
    }

    public void begin(Player player, Block block) {
        if (!playerOperationCapacity.hasAvailability(player.getUniqueId())) {
            reject(player, BloodstoneServerConstants.MACHINE_ALREADY_ACTIVATED);
            return;
        }
        ItemStack heldItem = player.getItemInHand();
        if (heldItem == null
                || !REPAIRABLE_ITEMS.contains(heldItem.getType())) {
            reject(player, BloodstoneServerConstants.ERROR_UNRECOGNIZED_ITEM);
            return;
        }
        if (itemService.isRestrictedFromModification(heldItem)) {
            reject(
                    player,
                    BloodstoneServerConstants.EFFECT_AXE_MODIFICATION_REJECTED
            );
            return;
        }
        if (heldItem.getDurability() < 1) {
            reject(player, BloodstoneServerConstants.REPAIR_FULL_DURABILITY);
            return;
        }
        ItemStack repaired = heldItem.clone();
        repaired.setDurability((short) 0);
        byte[] originalPayload;
        byte[] repairedPayload;
        try {
            originalPayload = BukkitItemSerialization.serializeItem(heldItem);
            repairedPayload = BukkitItemSerialization.serializeItem(repaired);
        } catch (IOException exception) {
            logger.log(
                    Level.SEVERE,
                    "Failed to serialize repair operation",
                    exception
            );
            return;
        }
        UUID operationId = UUID.randomUUID();
        if (!playerOperationCapacity.tryBegin(
                player.getUniqueId(),
                operationId
        )) {
            reject(player, BloodstoneServerConstants.MACHINE_ALREADY_ACTIVATED);
            return;
        }
        int heldSlot = player.getInventory().getHeldItemSlot();
        player.setItemInHand(itemIdentity.withOperationId(
                heldItem,
                operationId
        ));
        reserveWithRetry(
                operationId,
                player.getUniqueId(),
                originalPayload,
                Instant.now()
        ).thenAcceptAsync(outcome -> finishReservation(
                        player,
                        heldSlot,
                        block.getLocation(),
                        heldItem,
                        repaired,
                        heldItem.getDurability(),
                        repairedPayload,
                        operationId,
                        outcome
                ), mainThreadExecutor)
                .exceptionally(exception -> {
                    mainThreadExecutor.execute(() -> {
                        playerOperationCapacity.finish(operationId);
                        ItemStack current = player.getInventory()
                                .getItem(heldSlot);
                        if (current != null
                                && itemIdentity.operationId(current)
                                .filter(operationId::equals)
                                .isPresent()) {
                            player.getInventory().setItem(
                                    heldSlot,
                                    heldItem
                            );
                        }
                    });
                    logger.log(
                            Level.SEVERE,
                            "Failed to reserve repair operation",
                            exception
                    );
                    return null;
                });
    }

    public void shutdown() {
        acceptingOperations = false;
        animationDisplays.forEach(Item::remove);
        animationDisplays.clear();
    }

    private CompletableFuture<RepairReserveOutcome> reserveWithRetry(
            UUID operationId,
            UUID playerId,
            byte[] originalPayload,
            Instant startedAt
    ) {
        return storage.reserveRepairOperation(
                operationId,
                playerId,
                originalPayload,
                startedAt
        ).exceptionallyCompose(exception -> {
            if (!acceptingOperations) {
                return CompletableFuture.failedFuture(exception);
            }
            logger.log(
                    Level.WARNING,
                    "Retrying Bloodstone repair reservation " + operationId,
                    exception
            );
            return CompletableFuture.supplyAsync(
                    () -> null,
                    CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
            ).thenCompose(ignored -> reserveWithRetry(
                    operationId,
                    playerId,
                    originalPayload,
                    startedAt
            ));
        });
    }

    private void finishReservation(
            Player player,
            int heldSlot,
            Location anvilLocation,
            ItemStack original,
            ItemStack repaired,
            short originalDamage,
            byte[] repairedPayload,
            UUID operationId,
            RepairReserveOutcome outcome
    ) {
        if (!player.isOnline()) {
            playerOperationCapacity.finish(operationId);
            return;
        }

        ItemStack heldItem = player.getInventory().getItem(heldSlot);
        if (heldItem == null
                || itemIdentity.operationId(heldItem)
                .filter(operationId::equals).isEmpty()) {
            playerOperationCapacity.finish(operationId);
            reject(
                    player,
                    BloodstoneServerConstants.REPAIR_HELD_ITEM_RECOVERY
            );
            operationRecoveryService.recoverRepairOperation(
                    player,
                    operationId
            );
            return;
        }

        player.getInventory().clear(heldSlot);
        storage.markRepairOperationReady(
                        operationId,
                        player.getUniqueId(),
                        repairedPayload
                )
                .thenAcceptAsync(ready -> {
                    if (!ready) {
                        playerOperationCapacity.finish(operationId);
                        operationRecoveryService.recoverRepairOperation(
                                player,
                                operationId
                        );
                        throw new IllegalStateException(
                                "Repair operation disappeared before becoming ready"
                        );
                    }
                    playAnimation(
                            player,
                            anvilLocation,
                            repaired,
                            originalDamage,
                            operationId
                    );
                }, mainThreadExecutor)
                .exceptionally(exception -> {
                    logger.log(
                            Level.SEVERE,
                            "Failed to prepare repair operation",
                            exception
                    );
                    mainThreadExecutor.execute(() -> {
                        playerOperationCapacity.finish(operationId);
                        if (player.isOnline()) {
                            operationRecoveryService.recoverRepairOperation(
                                    player,
                                    operationId
                            );
                        }
                    });
                    return null;
                });
    }

    private void playAnimation(
            Player player,
            Location anvilLocation,
            ItemStack repaired,
            short startingDamage,
            UUID operationId
    ) {
        Item display = anvilLocation.getWorld().dropItem(
                anvilLocation.clone().add(0.5, 1.25, 0.5),
                repaired.clone()
        );
        animationDisplays.add(display);
        display.setPickupDelay(Integer.MAX_VALUE);
        display.setVelocity(new Vector());
        ItemStack visual = repaired.clone();
        visual.setDurability(startingDamage);
        display.setItemStack(visual);
        for (int step = 1; step <= 6; step++) {
            int animationStep = step;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (display.isDead()) {
                    return;
                }
                ItemStack updated = display.getItemStack();
                updated.setDurability((short) (
                        startingDamage * (6 - animationStep) / 6
                ));
                display.setItemStack(updated);
                display.getWorld().spigot().playEffect(
                        display.getLocation(),
                        Effect.TILE_BREAK,
                        Material.ANVIL.getId(),
                        0,
                        0.25F,
                        0.5F,
                        0.25F,
                        0.05F,
                        50,
                        48
                );
                float pitch = presentationService.randomPitch(1.0F, 1.5F);
                display.getWorld().playSound(
                        display.getLocation(),
                        Sound.ANVIL_LAND,
                        0.5F,
                        pitch
                );
                display.getWorld().playSound(
                        display.getLocation(),
                        Sound.ZOMBIE_METAL,
                        0.5F,
                        pitch
                );
                display.getWorld().playSound(
                        display.getLocation(),
                        Sound.DIG_STONE,
                        0.5F,
                        pitch
                );
            }, step * 8L);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            animationDisplays.remove(display);
            display.remove();
            if (!player.isOnline()) {
                playerOperationCapacity.finish(operationId);
                return;
            }
            deliveryService.deliver(
                            player,
                            operationId,
                            repaired,
                            false,
                            () -> storage.completeRepairOperation(
                                    operationId,
                                    player.getUniqueId()
                            )
                    )
                    .thenAcceptAsync(
                            ignored -> playerOperationCapacity.finish(
                                    operationId
                            ),
                            mainThreadExecutor
                    )
                    .exceptionally(exception -> {
                        logger.log(
                                Level.SEVERE,
                                "Failed to complete repair delivery",
                                exception
                        );
                        mainThreadExecutor.execute(() -> {
                            playerOperationCapacity.finish(operationId);
                            if (player.isOnline()) {
                                operationRecoveryService
                                        .recoverRepairOperation(
                                                player,
                                                operationId
                                        );
                            }
                        });
                        return null;
                    });
        }, 50L);
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }
}
