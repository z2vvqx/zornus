package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.RandomBoxRewards;
import net.valoury.bloodstone.server.RandomBoxRewards.RandomBoxReward;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.storage.BloodstoneOperationStorage;
import net.valoury.bloodstone.server.storage.RandomBoxReserveOutcome;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstoneRandomBoxService {

    private static final Sound REWARD_SOUND = Sound.ORB_PICKUP;

    private final Plugin plugin;
    private final BloodstoneOperationStorage storage;
    private final BloodstoneCurrencyService currencyService;
    private final BloodstoneOperationRecoveryService operationRecoveryService;
    private final BloodstoneReservedItemDeliveryService deliveryService;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstoneMessageService messageService;
    private final Logger logger;
    private final RandomBoxOperationCoordinator<RandomBoxBlockPosition>
            operationCoordinator = new RandomBoxOperationCoordinator<>();
    private final Set<Item> animationDisplays = new HashSet<>();

    private volatile boolean acceptingOperations = true;

    public BloodstoneRandomBoxService(
            Plugin plugin,
            BloodstoneOperationStorage storage,
            BloodstoneCurrencyService currencyService,
            BloodstoneOperationRecoveryService operationRecoveryService,
            BloodstoneReservedItemDeliveryService deliveryService,
            BloodstonePresentationService presentationService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstoneMessageService messageService,
            Logger logger
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.currencyService = currencyService;
        this.operationRecoveryService = operationRecoveryService;
        this.deliveryService = deliveryService;
        this.presentationService = presentationService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.messageService = messageService;
        this.logger = logger;
    }

    public void begin(Player player, Block block) {
        Location blockLocation = block.getLocation();
        UUID operationId = UUID.randomUUID();
        RandomBoxOperationCoordinator.BeginOutcome beginOutcome =
                beginOperation(
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
            reject(player, BloodstoneServerConstants.MACHINE_ALREADY_ACTIVATED);
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            finishOperation(blockLocation, operationId);
            reject(player, BloodstoneServerConstants.ERROR_INVENTORY_SPACE);
            return;
        }
        BloodstoneRank rank = BloodstoneRank.resolve(player);
        RandomBoxReward reward = RandomBoxRewards.roll();
        byte[] rewardPayload;
        try {
            rewardPayload = BukkitItemSerialization.serializeItem(
                    reward.createItem()
            );
        } catch (IOException exception) {
            finishOperation(blockLocation, operationId);
            logger.log(
                    Level.SEVERE,
                    "Failed to serialize Random Box reward",
                    exception
            );
            return;
        }
        Instant startedAt = Instant.now();
        reserveWithRetry(
                operationId,
                player.getUniqueId(),
                reward,
                rewardPayload,
                rank,
                false,
                startedAt
        ).thenAcceptAsync(outcome -> finishInitialReservation(
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
                    mainThreadExecutor.execute(() ->
                            finishOperation(blockLocation, operationId));
                    logger.log(
                            Level.SEVERE,
                            "Failed to reserve Random Box operation",
                            exception
                    );
                    return null;
                });
    }

    public void shutdown() {
        acceptingOperations = false;
        animationDisplays.forEach(Item::remove);
        animationDisplays.clear();
        operationCoordinator.clear();
    }

    private CompletableFuture<RandomBoxReserveOutcome> reserveWithRetry(
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
            logger.log(
                    Level.WARNING,
                    "Retrying Random Box reservation " + operationId,
                    exception
            );
            return CompletableFuture.supplyAsync(
                    () -> null,
                    CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
            ).thenCompose(ignored -> reserveWithRetry(
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

    private void finishInitialReservation(
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
            finishOperation(blockLocation, operationId);
            return;
        }
        if (outcome instanceof RandomBoxReserveOutcome.PaymentRequired) {
            if (!currencyService.removeBlood(
                    player.getInventory(),
                    rank.randomBoxBloodCost()
            )) {
                finishOperation(blockLocation, operationId);
                messageService.sendRequiredCurrency(
                        player,
                        rank.randomBoxBloodCost(),
                        BloodstoneMessageService.Currency.BLOOD
                );
                return;
            }
            reserveWithRetry(
                    operationId,
                    player.getUniqueId(),
                    reward,
                    rewardPayload,
                    rank,
                    true,
                    startedAt
            ).thenAcceptAsync(paidOutcome -> finishReservation(
                            player,
                            blockLocation,
                            reward,
                            operationId,
                            paidOutcome
                    ), mainThreadExecutor)
                    .exceptionally(exception -> {
                        mainThreadExecutor.execute(() -> {
                            finishOperation(blockLocation, operationId);
                            if (player.isOnline()) {
                                refundBlood(
                                        player,
                                        rank.randomBoxBloodCost()
                                );
                                reject(
                                        player,
                                        BloodstoneServerConstants
                                                .RANDOM_BOX_RESERVATION_REFUNDED
                                );
                            }
                        });
                        logger.log(
                                Level.SEVERE,
                                "Failed to reserve paid Random Box operation "
                                        + operationId,
                                exception
                        );
                        return null;
                    });
            return;
        }
        finishReservation(
                player,
                blockLocation,
                reward,
                operationId,
                outcome
        );
    }

    private void finishReservation(
            Player player,
            Location blockLocation,
            RandomBoxReward reward,
            UUID operationId,
            RandomBoxReserveOutcome outcome
    ) {
        if (!player.isOnline()) {
            finishOperation(blockLocation, operationId);
            return;
        }
        if (outcome instanceof RandomBoxReserveOutcome.PaymentRequired) {
            finishOperation(blockLocation, operationId);
            refundBlood(
                    player,
                    BloodstoneRank.resolve(player).randomBoxBloodCost()
            );
            reject(
                    player,
                    BloodstoneServerConstants.RANDOM_BOX_PAYMENT_REJECTED
            );
            return;
        }
        if (outcome instanceof RandomBoxReserveOutcome.AlreadyCompleted) {
            finishOperation(blockLocation, operationId);
            return;
        }
        RandomBoxOperation operation =
                ((RandomBoxReserveOutcome.Reserved) outcome).operation();
        activateOperation(blockLocation, operationId);
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
        player.playSound(
                player.getLocation(),
                Sound.LEVEL_UP,
                0.5F,
                presentationService.randomPitch(1.5F, 2.0F)
        );
        playStep(player, blockLocation, operation, reward, 0, null);
    }

    private void playStep(
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
            finish(player, blockLocation, operation);
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
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> playStep(
                        player,
                        blockLocation,
                        operation,
                        selectedReward,
                        step + 1,
                        display
                ),
                10L
        );
    }

    private void finish(
            Player player,
            Location blockLocation,
            RandomBoxOperation operation
    ) {
        if (!player.isOnline()) {
            finishOperation(blockLocation, operation.operationId());
            return;
        }
        ItemStack rewardItem;
        try {
            rewardItem = BukkitItemSerialization.deserializeItem(
                    operation.rewardPayload()
            );
        } catch (IOException exception) {
            logger.log(
                    Level.SEVERE,
                    "Failed to deserialize Random Box delivery",
                    exception
            );
            finishOperation(blockLocation, operation.operationId());
            return;
        }
        deliveryService.deliver(
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
                    finishOperation(blockLocation, operation.operationId());
                    if (outcome.wasDelivered() && player.isOnline()) {
                        player.playSound(
                                player.getLocation(),
                                REWARD_SOUND,
                                1.0F,
                                1.9F
                        );
                    }
                }, mainThreadExecutor)
                .exceptionally(exception -> {
                    logger.log(
                            Level.SEVERE,
                            "Failed to complete Random Box delivery",
                            exception
                    );
                    mainThreadExecutor.execute(() -> {
                        finishOperation(
                                blockLocation,
                                operation.operationId()
                        );
                        if (player.isOnline()) {
                            operationRecoveryService
                                    .recoverRandomBoxOperation(
                                            player,
                                            operation.operationId()
                                    );
                        }
                    });
                    return null;
                });
    }

    private void refundBlood(Player player, int amount) {
        int leftovers = currencyService.addBlood(
                player.getInventory(),
                amount
        );
        if (leftovers > 0) {
            player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    currencyService.createBlood(leftovers)
            );
        }
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }

    private RandomBoxOperationCoordinator.BeginOutcome beginOperation(
            Location blockLocation,
            UUID operationId,
            UUID playerId
    ) {
        return operationCoordinator.tryBegin(
                RandomBoxBlockPosition.from(blockLocation),
                operationId,
                playerId
        );
    }

    private void activateOperation(
            Location blockLocation,
            UUID operationId
    ) {
        operationCoordinator.activate(
                RandomBoxBlockPosition.from(blockLocation),
                operationId
        );
    }

    private void finishOperation(
            Location blockLocation,
            UUID operationId
    ) {
        operationCoordinator.finish(
                RandomBoxBlockPosition.from(blockLocation),
                operationId
        );
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
