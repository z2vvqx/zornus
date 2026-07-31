package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.RandomBoxRewards;
import net.valoury.bloodstone.server.RandomBoxRewards.RandomBoxReward;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import net.valoury.bloodstone.server.storage.RandomBoxReserveOutcome;
import net.valoury.bloodstone.server.storage.RepairReserveOutcome;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstoneMachineService {

    private static final int MAXIMUM_CONCURRENT_REPAIR_OPERATIONS = 4;
    private static final int DEFAULT_REPAIR_BLOOD_COST = 20;
    private static final Sound RANDOM_BOX_REWARD_SOUND = Sound.ORB_PICKUP;

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
    private final BloodstoneStorage storage;
    private final BloodstoneItemService itemService;
    private final BloodstoneCombatService combatService;
    private final BloodstoneMenuService menuService;
    private final BloodstoneStorageService storageService;
    private final BloodstoneEnchanterService enchanterService;
    private final BloodstoneAxeFuserService axeFuserService;
    private final BloodstonePlayerService playerService;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstoneMessageService messageService;
    private final Logger logger;

    private final RandomBoxOperationCoordinator<RandomBoxBlockPosition>
            randomBoxOperationCoordinator =
                    new RandomBoxOperationCoordinator<>();
    private final OperationCapacity activeRepairOperations = new OperationCapacity(
            MAXIMUM_CONCURRENT_REPAIR_OPERATIONS
    );
    private final Set<Item> animationDisplays = new java.util.HashSet<>();
    private volatile boolean acceptingOperations = true;

    public BloodstoneMachineService(
            Plugin plugin,
            BloodstoneStorage storage,
            BloodstoneItemService itemService,
            BloodstoneCombatService combatService,
            BloodstoneMenuService menuService,
            BloodstoneStorageService storageService,
            BloodstoneEnchanterService enchanterService,
            BloodstoneAxeFuserService axeFuserService,
            BloodstonePlayerService playerService,
            BloodstonePresentationService presentationService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstoneMessageService messageService,
            Logger logger
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.itemService = itemService;
        this.combatService = combatService;
        this.menuService = menuService;
        this.storageService = storageService;
        this.enchanterService = enchanterService;
        this.axeFuserService = axeFuserService;
        this.playerService = playerService;
        this.presentationService = presentationService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.messageService = messageService;
        this.logger = logger;
    }

    public boolean isUnavailable(UUID playerId) {
        return !acceptingOperations || !playerService.isLoaded(playerId);
    }

    public void handleBlockInteraction(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null || !isBloodstone(player) || player.getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        if (!playerService.isLoaded(player.getUniqueId())) {
            event.setCancelled(true);
            reject(player, BloodstoneServerConstants.PLAYER_DATA_LOADING);
            return;
        }
        if (!acceptingOperations) {
            event.setCancelled(true);
            reject(player, BloodstoneServerConstants.ERROR_SHUTTING_DOWN);
            return;
        }
        Material material = block.getType();
        if (material == Material.ENDER_CHEST && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            storageService.openStorageMenu(player);
        } else if (material == Material.ENDER_PORTAL_FRAME
                && event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            enchanterService.openRankDisenchanter(player, block);
        } else if (material == Material.ENDER_PORTAL_FRAME
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            enchanterService.openRankEnchanter(player, block);
        } else if (material == Material.ANVIL && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            beginRepair(player, block);
        } else if (material == Material.FURNACE
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            axeFuserService.open(player, block);
        } else if (isPistonHead(material) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            beginRandomBox(player, block);
        } else if (material == Material.REDSTONE_BLOCK) {
            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                menuService.exchangeBloodForAlloy(player);
            } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                menuService.exchangeAlloyForBlood(player);
            }
        } else if ((material == Material.SIGN_POST || material == Material.WALL_SIGN)
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && block.getState() instanceof Sign sign) {
            event.setCancelled(true);
            handleSign(player, sign);
        }
    }

    public void handleItemFrame(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame itemFrame)
                || !isBloodstone(event.getPlayer())
                || event.getPlayer().getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        event.setCancelled(true);
        ItemStack displayed = itemFrame.getItem();
        if (displayed == null || displayed.getType() == Material.AIR) {
            return;
        }
        ItemStack reward = displayed.clone();
        reward.setAmount(reward.getMaxStackSize());
        if (!canFit(event.getPlayer(), reward)) {
            reject(event.getPlayer(), BloodstoneServerConstants.ERROR_INVENTORY_SPACE);
            return;
        }
        event.getPlayer().getInventory().addItem(reward);
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ITEM_PICKUP, 1.0F,
                presentationService.randomPitch(0.9F, 1.1F));
    }

    public void handleResistancePotion(PlayerItemConsumeEvent event) {
        if (!isBloodstone(event.getPlayer()) || !itemService.isResistancePotion(event.getItem())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            event.getPlayer().addPotionEffect(itemService.createResistanceEffect(), true);
        });
    }

    public void handleBloodPickup(PlayerPickupItemEvent event) {
        if (!isBloodstone(event.getPlayer())) {
            return;
        }
        if (itemService.isBlood(event.getItem().getItemStack())) {
            event.getPlayer().playSound(
                    event.getPlayer().getLocation(),
                    Sound.ITEM_PICKUP,
                    0.35F,
                    1.4F
            );
        }
    }

    public void handleDisposableItemSpawn(ItemSpawnEvent event) {
        if (!"bloodstone".equals(event.getLocation().getWorld().getName())) {
            return;
        }
        Material material = event.getEntity().getItemStack().getType();
        if (material == Material.GOLDEN_APPLE || material == Material.ARROW) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!event.getEntity().isDead()) {
                    event.getEntity().remove();
                }
            }, 160L);
        }
    }

    private void beginRandomBox(Player player, Block block) {
        Location blockLocation = block.getLocation();
        UUID operationId = UUID.randomUUID();
        RandomBoxOperationCoordinator.BeginOutcome beginOutcome =
                beginRandomBoxOperation(
                        blockLocation,
                        operationId,
                        player.getUniqueId()
                );
        if (beginOutcome
                == RandomBoxOperationCoordinator.BeginOutcome
                        .ALREADY_PENDING_BY_PLAYER) {
            return;
        }
        if (beginOutcome
                == RandomBoxOperationCoordinator.BeginOutcome.RESOURCE_IN_USE) {
            reject(player, BloodstoneServerConstants.RANDOM_BOX_BLOCK_IN_USE);
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            finishRandomBoxOperation(blockLocation, operationId);
            reject(player, BloodstoneServerConstants.ERROR_INVENTORY_SPACE);
            return;
        }
        BloodstoneRank rank = BloodstoneRank.resolve(player);
        RandomBoxReward reward = RandomBoxRewards.roll();
        ItemStack rewardItem = reward.createItem();
        byte[] rewardPayload;
        try {
            rewardPayload = BukkitItemSerialization.serializeItem(rewardItem);
        } catch (IOException exception) {
            finishRandomBoxOperation(blockLocation, operationId);
            logger.log(Level.SEVERE, "Failed to serialize Random Box reward", exception);
            return;
        }
        Instant startedAt = Instant.now();
        reserveRandomBoxWithRetry(
                operationId,
                player.getUniqueId(),
                reward,
                rewardPayload,
                rank,
                false,
                startedAt
        ).thenAcceptAsync(outcome -> finishInitialRandomBoxReservation(
                        player,
                        blockLocation,
                        reward,
                        rewardPayload,
                        rank,
                        operationId,
                        startedAt,
                        outcome
                ), mainThreadExecutor)
                .exceptionally(exception -> {
                    mainThreadExecutor.execute(() -> {
                        finishRandomBoxOperation(blockLocation, operationId);
                    });
                    logger.log(Level.SEVERE, "Failed to reserve Random Box operation", exception);
                    return null;
                });
    }

    private CompletableFuture<RandomBoxReserveOutcome> reserveRandomBoxWithRetry(
            UUID operationId,
            UUID playerId,
            RandomBoxReward reward,
            byte[] rewardPayload,
            BloodstoneRank rank,
            boolean paidUseAllowed,
            Instant startedAt
    ) {
        return storage.reserveRandomBox(
                operationId,
                playerId,
                reward.id(),
                rewardPayload,
                rank.freeRandomBoxes(),
                rank.randomBoxBloodCost(),
                paidUseAllowed,
                startedAt
        ).exceptionallyCompose(exception -> {
            if (!acceptingOperations) {
                return CompletableFuture.failedFuture(exception);
            }
            logger.log(Level.WARNING,
                    "Retrying Random Box reservation " + operationId,
                    exception);
            return CompletableFuture.supplyAsync(
                    () -> null,
                    CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
            ).thenCompose(ignored -> reserveRandomBoxWithRetry(
                    operationId,
                    playerId,
                    reward,
                    rewardPayload,
                    rank,
                    paidUseAllowed,
                    startedAt
            ));
        });
    }

    private void finishInitialRandomBoxReservation(
            Player player,
            Location blockLocation,
            RandomBoxReward reward,
            byte[] rewardPayload,
            BloodstoneRank rank,
            UUID operationId,
            Instant startedAt,
            RandomBoxReserveOutcome outcome
    ) {
        if (!player.isOnline()) {
            finishRandomBoxOperation(blockLocation, operationId);
            return;
        }
        if (outcome instanceof RandomBoxReserveOutcome.PaymentRequired) {
            if (!itemService.removeBlood(player.getInventory(), rank.randomBoxBloodCost())) {
                finishRandomBoxOperation(blockLocation, operationId);
                messageService.sendRequiredCurrency(
                        player,
                        rank.randomBoxBloodCost(),
                        BloodstoneMessageService.Currency.BLOOD
                );
                return;
            }
            reserveRandomBoxWithRetry(
                    operationId,
                    player.getUniqueId(),
                    reward,
                    rewardPayload,
                    rank,
                    true,
                    startedAt
            ).thenAcceptAsync(paidOutcome -> finishRandomBoxReservation(
                            player,
                            blockLocation,
                            reward,
                            operationId,
                            paidOutcome
                    ), mainThreadExecutor)
                    .exceptionally(exception -> {
                        mainThreadExecutor.execute(() -> {
                            finishRandomBoxOperation(blockLocation, operationId);
                            if (player.isOnline()) {
                                refundBlood(player, rank.randomBoxBloodCost());
                                reject(
                                        player,
                                        BloodstoneServerConstants
                                                .RANDOM_BOX_RESERVATION_REFUNDED
                                );
                            }
                        });
                        logger.log(Level.SEVERE,
                                "Failed to reserve paid Random Box operation " + operationId,
                                exception);
                        return null;
                    });
            return;
        }
        finishRandomBoxReservation(player, blockLocation, reward, operationId, outcome);
    }

    private void finishRandomBoxReservation(
            Player player,
            Location blockLocation,
            RandomBoxReward reward,
            UUID operationId,
            RandomBoxReserveOutcome outcome
    ) {
        if (!player.isOnline()) {
            finishRandomBoxOperation(blockLocation, operationId);
            return;
        }
        if (outcome instanceof RandomBoxReserveOutcome.PaymentRequired) {
            finishRandomBoxOperation(blockLocation, operationId);
            refundBlood(
                    player,
                    BloodstoneRank.resolve(player).randomBoxBloodCost()
            );
            reject(player, BloodstoneServerConstants.RANDOM_BOX_PAYMENT_REJECTED);
            return;
        }
        if (outcome instanceof RandomBoxReserveOutcome.AlreadyCompleted) {
            finishRandomBoxOperation(blockLocation, operationId);
            return;
        }
        RandomBoxOperation operation =
                ((RandomBoxReserveOutcome.Reserved) outcome).operation();
        activateRandomBoxOperation(blockLocation, operationId);
        if (!operation.freeUse()) {
            BloodstoneText.sendActionBar(
                    player,
                    BloodstoneServerConstants.RANDOM_BOX_COST_ACTION_BAR_FORMAT,
                    Placeholder.unparsed(
                            "cost",
                            Integer.toString(operation.bloodCost())
                    )
            );
        }
        BloodstoneText.sendMessage(
                player,
                BloodstoneServerConstants.RANDOM_BOX_WHOOSH
        );
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 0.5F,
                presentationService.randomPitch(1.5F, 2.0F));
        playRandomBoxStep(player, blockLocation, operation, reward, 0, null);
    }

    private void playRandomBoxStep(
            Player player,
            Location blockLocation,
            RandomBoxOperation operation,
            RandomBoxReward selectedReward,
            int step,
            @Nullable Item previousDisplay
    ) {
        if (previousDisplay != null) {
            animationDisplays.remove(previousDisplay);
            previousDisplay.remove();
        }
        if (step >= 8) {
            finishRandomBox(player, blockLocation, operation, selectedReward);
            return;
        }
        RandomBoxReward displayReward = step == 7
                ? selectedReward
                : RandomBoxRewards.roll();
        Item display = blockLocation.getWorld().dropItem(
                blockLocation.clone().add(0.5, 1.25, 0.5),
                displayReward.createItem()
        );
        animationDisplays.add(display);
        display.setPickupDelay(Integer.MAX_VALUE);
        display.setVelocity(new Vector());
        if (step >= 5) {
            blockLocation.getWorld().playSound(
                    blockLocation,
                    Sound.NOTE_SNARE_DRUM,
                    0.5F,
                    step == 7
                            ? presentationService.randomPitch(1.8F, 2.0F)
                            : presentationService.randomPitch(1.2F, 1.5F)
            );
        }
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> playRandomBoxStep(player, blockLocation, operation, selectedReward, step + 1, display),
                10L);
    }

    private void finishRandomBox(
            Player player,
            Location blockLocation,
            RandomBoxOperation operation,
            RandomBoxReward selectedReward
    ) {
        if (!player.isOnline()) {
            finishRandomBoxOperation(blockLocation, operation.operationId());
            return;
        }
        ItemStack rewardItem;
        try {
            rewardItem = BukkitItemSerialization.deserializeItem(operation.rewardPayload());
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Failed to deserialize Random Box delivery", exception);
            finishRandomBoxOperation(blockLocation, operation.operationId());
            return;
        }
        playerService.deliverReservedItem(
                        player,
                        operation.operationId(),
                        rewardItem,
                        false,
                        () -> storage.completeRandomBox(
                                operation.operationId(),
                                player.getUniqueId()
                        )
                )
                .thenAcceptAsync(outcome -> {
                    finishRandomBoxOperation(blockLocation, operation.operationId());
                    if (outcome.wasDelivered()
                            && player.isOnline()) {
                        player.playSound(
                                player.getLocation(),
                                RANDOM_BOX_REWARD_SOUND,
                                1.0F,
                                1.9F
                        );
                    }
                }, mainThreadExecutor)
                .exceptionally(exception -> {
                    logger.log(Level.SEVERE, "Failed to complete Random Box delivery", exception);
                    mainThreadExecutor.execute(() -> {
                        finishRandomBoxOperation(blockLocation, operation.operationId());
                        if (player.isOnline()) {
                            playerService.recoverRandomBoxOperation(
                                    player,
                                    operation.operationId()
                            );
                        }
                    });
                    return null;
                });
    }

    private void beginRepair(Player player, Block block) {
        if (!activeRepairOperations.hasAvailability()) {
            reject(player, BloodstoneServerConstants.REPAIR_CAPACITY_REACHED);
            return;
        }
        ItemStack heldItem = player.getItemInHand();
        if (heldItem == null || !REPAIRABLE_ITEMS.contains(heldItem.getType())) {
            reject(player, BloodstoneServerConstants.ERROR_UNRECOGNIZED_ITEM);
            return;
        }
        if (heldItem.getDurability() < 1) {
            reject(player, BloodstoneServerConstants.REPAIR_FULL_DURABILITY);
            return;
        }
        if (itemService.isSoulbound(heldItem) || itemService.isExclusive(heldItem)) {
            reject(player, BloodstoneServerConstants.REPAIR_PROTECTED_ITEM);
            return;
        }
        boolean free = BloodstoneRank.resolve(player).isPaid();
        if (!free && itemService.countBlood(player.getInventory())
                < DEFAULT_REPAIR_BLOOD_COST) {
            messageService.sendRequiredCurrency(
                    player,
                    DEFAULT_REPAIR_BLOOD_COST,
                    BloodstoneMessageService.Currency.BLOOD
            );
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
            logger.log(Level.SEVERE, "Failed to serialize repair operation", exception);
            return;
        }
        UUID operationId = UUID.randomUUID();
        if (!activeRepairOperations.tryBegin(operationId)) {
            reject(player, BloodstoneServerConstants.REPAIR_CAPACITY_REACHED);
            return;
        }
        int heldSlot = player.getInventory().getHeldItemSlot();
        player.setItemInHand(itemService.withOperationId(heldItem, operationId));
        reserveRepairWithRetry(
                operationId,
                player.getUniqueId(),
                originalPayload,
                Instant.now()
        )
                .thenAcceptAsync(outcome -> finishRepairReservation(
                        player,
                        heldSlot,
                        block.getLocation(),
                        heldItem,
                        repaired,
                        heldItem.getDurability(),
                        repairedPayload,
                        free,
                        operationId,
                        outcome
                ), mainThreadExecutor)
                .exceptionally(exception -> {
                    mainThreadExecutor.execute(() -> {
                        activeRepairOperations.finish(operationId);
                        ItemStack current = player.getInventory().getItem(heldSlot);
                        if (current != null
                                && itemService.operationId(current)
                                .filter(operationId::equals)
                                .isPresent()) {
                            player.getInventory().setItem(heldSlot, heldItem);
                        }
                    });
                    logger.log(Level.SEVERE, "Failed to reserve repair operation", exception);
                    return null;
                });
    }

    private CompletableFuture<RepairReserveOutcome> reserveRepairWithRetry(
            UUID operationId,
            UUID playerId,
            byte[] originalPayload,
            Instant startedAt
    ) {
        return storage.reserveRepairOperation(operationId, playerId, originalPayload, startedAt)
                .exceptionallyCompose(exception -> {
                    if (!acceptingOperations) {
                        return CompletableFuture.failedFuture(exception);
                    }
                    logger.log(Level.WARNING,
                            "Retrying Bloodstone repair reservation " + operationId,
                            exception);
                    return CompletableFuture.supplyAsync(
                            () -> null,
                            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
                    ).thenCompose(ignored ->
                            reserveRepairWithRetry(operationId, playerId, originalPayload, startedAt));
                });
    }

    private void finishRepairReservation(
            Player player,
            int heldSlot,
            Location anvilLocation,
            ItemStack original,
            ItemStack repaired,
            short originalDamage,
            byte[] repairedPayload,
            boolean free,
            UUID operationId,
            RepairReserveOutcome outcome
    ) {
        if (!player.isOnline()) {
            activeRepairOperations.finish(operationId);
            return;
        }

        ItemStack heldItem = player.getInventory().getItem(heldSlot);
        if (heldItem == null
                || itemService.operationId(heldItem).filter(operationId::equals).isEmpty()) {
            activeRepairOperations.finish(operationId);
            reject(player, BloodstoneServerConstants.REPAIR_HELD_ITEM_RECOVERY);
            playerService.recoverRepairOperation(player, operationId);
            return;
        }

        if (!free && !itemService.removeBlood(
                player.getInventory(),
                DEFAULT_REPAIR_BLOOD_COST
        )) {
            activeRepairOperations.finish(operationId);
            playerService.deliverReservedItem(
                    player,
                    operationId,
                    original,
                    false,
                    () -> storage.completeRepairOperation(operationId, player.getUniqueId())
            );
            messageService.sendRequiredCurrency(
                    player,
                    DEFAULT_REPAIR_BLOOD_COST,
                    BloodstoneMessageService.Currency.BLOOD
            );
            return;
        }
        player.getInventory().clear(heldSlot);
        storage.markRepairOperationReady(operationId, player.getUniqueId(), repairedPayload)
                .thenAcceptAsync(ready -> {
                    if (!ready) {
                        activeRepairOperations.finish(operationId);
                        playerService.recoverRepairOperation(player, operationId);
                        throw new IllegalStateException(
                                "Repair operation disappeared before becoming ready"
                        );
                    }
                    playRepairAnimation(player, anvilLocation, repaired, originalDamage, operationId);
                }, mainThreadExecutor)
                .exceptionally(exception -> {
                    logger.log(Level.SEVERE, "Failed to prepare repair operation", exception);
                    mainThreadExecutor.execute(() -> {
                        activeRepairOperations.finish(operationId);
                        if (player.isOnline()) {
                            playerService.recoverRepairOperation(player, operationId);
                        }
                    });
                    return null;
                });
    }

    private void playRepairAnimation(
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
                updated.setDurability((short) (startingDamage * (6 - animationStep) / 6));
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
                        display.getLocation(), Sound.ANVIL_LAND, 0.5F, pitch);
                display.getWorld().playSound(
                        display.getLocation(), Sound.ZOMBIE_METAL, 0.5F, pitch);
                display.getWorld().playSound(
                        display.getLocation(), Sound.DIG_STONE, 0.5F, pitch);
            }, step * 8L);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            animationDisplays.remove(display);
            display.remove();
            if (!player.isOnline()) {
                activeRepairOperations.finish(operationId);
                return;
            }
            playerService.deliverReservedItem(
                            player,
                            operationId,
                            repaired,
                            false,
                            () -> storage.completeRepairOperation(
                                    operationId,
                                    player.getUniqueId()
                            )
                    )
                    .thenAcceptAsync(ignored ->
                                    activeRepairOperations.finish(operationId),
                            mainThreadExecutor)
                    .exceptionally(exception -> {
                        logger.log(Level.SEVERE, "Failed to complete repair delivery", exception);
                        mainThreadExecutor.execute(() -> {
                            activeRepairOperations.finish(operationId);
                            if (player.isOnline()) {
                                playerService.recoverRepairOperation(player, operationId);
                            }
                        });
                        return null;
                    });
        }, 50L);
    }

    public void shutdown() {
        acceptingOperations = false;
        animationDisplays.forEach(Item::remove);
        animationDisplays.clear();
        randomBoxOperationCoordinator.clear();
        activeRepairOperations.clear();
    }

    private void refundBlood(Player player, int amount) {
        int leftovers = itemService.addBlood(player.getInventory(), amount);
        if (leftovers > 0) {
            player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    itemService.createBlood(leftovers)
            );
        }
    }

    private void handleSign(Player player, Sign sign) {
        String instruction = sign.getLine(1).trim();
        if (instruction.equalsIgnoreCase("spawn")) {
            player.performCommand("spawn");
            player.playSound(player.getLocation(), Sound.NOTE_STICKS, 1.0F,
                    presentationService.randomPitch(0.9F, 1.1F));
        } else if (instruction.equalsIgnoreCase("heal")) {
            heal(player);
        } else if (instruction.equalsIgnoreCase("trash")) {
            menuService.openTrash(player);
        } else if (instruction.equalsIgnoreCase("EXP")) {
            giveExperience(player, sign.getLocation());
        }
    }

    private void heal(Player player) {
        boolean removed = false;
        for (PotionEffectType harmfulEffect : List.of(
                PotionEffectType.POISON,
                PotionEffectType.WITHER,
                PotionEffectType.WEAKNESS,
                PotionEffectType.BLINDNESS
        )) {
            if (player.hasPotionEffect(harmfulEffect)) {
                player.removePotionEffect(harmfulEffect);
                removed = true;
            }
        }
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        presentationService.playHeal(player, removed);
    }

    private void giveExperience(Player player, Location signLocation) {
        if (combatService.isTagged(player.getUniqueId())) {
            reject(player, BloodstoneServerConstants.ERROR_IN_BATTLE);
            return;
        }
        boolean ranked = BloodstoneRank.resolve(player).isPaid();
        if (!ranked && !itemService.removeBlood(player.getInventory(), 1)) {
            messageService.sendRequiredCurrency(
                    player,
                    1,
                    BloodstoneMessageService.Currency.BLOOD
            );
            return;
        }
        int levels = ranked
                ? ThreadLocalRandom.current().nextInt(1, 6)
                : ThreadLocalRandom.current().nextInt(1, 3);
        float progress = ranked
                ? (float) ThreadLocalRandom.current().nextDouble(0.25, 0.75)
                : (float) ThreadLocalRandom.current().nextDouble(0.25, 0.50);
        float combinedProgress = player.getExp() + progress;
        int bonusLevels = (int) Math.floor(combinedProgress);
        player.setLevel(player.getLevel() + levels + bonusLevels);
        player.setExp(combinedProgress - bonusLevels);
        Location particleOrigin = signLocation.clone().add(0.5, 0.7, 0.5);
        for (int step = 0; step < 8; step++) {
            int animationStep = step;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                double progressToPlayer = (animationStep + 1) / 8.0;
                Location playerTarget = player.getLocation().clone().add(0.0, 1.0, 0.0);
                Vector path = playerTarget.toVector().subtract(particleOrigin.toVector())
                        .multiply(progressToPlayer);
                Location particleLocation = particleOrigin.clone().add(path);
                particleLocation.getWorld().spigot().playEffect(
                        particleLocation,
                        Effect.FLYING_GLYPH,
                        0,
                        0,
                        0.16F,
                        0.16F,
                        0.16F,
                        0.04F,
                        8,
                        48
                );
            }, step * 2L);
        }
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1.0F, 1.2F);
        if (ThreadLocalRandom.current().nextDouble() < 0.025) {
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0F, 1.1F);
        }
        BloodstoneText.sendActionBar(
                player,
                BloodstoneServerConstants.EXPERIENCE_REWARD_ACTION_BAR_FORMAT,
                Placeholder.unparsed("levels", Integer.toString(levels)),
                Placeholder.unparsed(
                        "progress",
                        String.format(Locale.US, "%.2f", progress)
                )
        );
    }

    private boolean isPistonHead(Material material) {
        return material == Material.PISTON_EXTENSION || material.name().equals("PISTON_HEAD");
    }

    private boolean canFit(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() >= 0) {
            return true;
        }
        for (ItemStack existing : player.getInventory().getContents()) {
            if (existing != null && existing.isSimilar(item)
                    && existing.getAmount() + item.getAmount() <= existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }

    private RandomBoxOperationCoordinator.BeginOutcome beginRandomBoxOperation(
            Location blockLocation,
            UUID operationId,
            UUID playerId
    ) {
        RandomBoxBlockPosition blockPosition =
                RandomBoxBlockPosition.from(blockLocation);
        return randomBoxOperationCoordinator.tryBegin(
                blockPosition,
                operationId,
                playerId
        );
    }

    private void activateRandomBoxOperation(
            Location blockLocation,
            UUID operationId
    ) {
        randomBoxOperationCoordinator.activate(
                RandomBoxBlockPosition.from(blockLocation),
                operationId
        );
    }

    private void finishRandomBoxOperation(
            Location blockLocation,
            UUID operationId
    ) {
        randomBoxOperationCoordinator.finish(
                RandomBoxBlockPosition.from(blockLocation),
                operationId
        );
    }

    private boolean isBloodstone(Player player) {
        return "bloodstone".equals(player.getWorld().getName());
    }

    private record RandomBoxBlockPosition(
            UUID worldId,
            int blockX,
            int blockY,
            int blockZ
    ) {
        private static RandomBoxBlockPosition from(Location location) {
            return new RandomBoxBlockPosition(
                    location.getWorld().getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
        }
    }
}
