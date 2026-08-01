package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.CombinedEffectAxeDefinitions;
import net.valoury.bloodstone.server.CombinedEffectAxeDefinitions.CombinedEffectAxeDefinition;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectAxeDefinition;
import net.valoury.bloodstone.server.model.AxeFuserOperation;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.storage.AxeFuserReserveOutcome;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstoneAxeFuserService {

    static final int FUSION_BLOOD_ALLOY_COST = 16;
    private final BloodstoneStorage storage;
    private final BloodstoneItemService itemService;
    private final BloodstoneCombatService combatService;
    private final BloodstonePlayerService playerService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMessageService messageService;
    private final Logger logger;
    private final AxeFuserMenuView menuView;
    private final AxeFuserAnimation animation;

    private final Map<UUID, AxeFuserContext> contexts = new HashMap<>();
    private final ExclusiveOperationResources<AxeFuserBlockPosition>
            activeFuserBlocks = new ExclusiveOperationResources<>();
    private final ExclusiveOperationResources<UUID> activeFuserPlayers =
            new ExclusiveOperationResources<>();
    private volatile boolean acceptingOperations = true;

    public BloodstoneAxeFuserService(
            Plugin plugin,
            BloodstoneStorage storage,
            BloodstoneItemService itemService,
            BloodstoneCombatService combatService,
            BloodstonePlayerService playerService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstonePresentationService presentationService,
            BloodstoneMessageService messageService,
            Logger logger
    ) {
        this.storage = storage;
        this.itemService = itemService;
        this.combatService = combatService;
        this.playerService = playerService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.presentationService = presentationService;
        this.messageService = messageService;
        this.logger = logger;
        this.menuView = new AxeFuserMenuView(itemService);
        this.animation = new AxeFuserAnimation(
                plugin,
                storage,
                playerService,
                mainThreadExecutor,
                presentationService,
                logger
        );
    }

    public void open(Player player, Block furnace) {
        if (!acceptingOperations) {
            reject(player, BloodstoneServerConstants.ERROR_SHUTTING_DOWN);
            return;
        }
        BloodstoneRank rank = BloodstoneRank.resolve(player);
        if (!hasAxeFuserAccess(rank)) {
            reject(
                    player,
                    BloodstoneServerConstants.AXE_FUSER_ACCESS_REQUIRED
            );
            return;
        }
        if (combatService.isTagged(player.getUniqueId())) {
            reject(player, BloodstoneServerConstants.ERROR_IN_BATTLE);
            return;
        }
        Inventory inventory = menuView.createInventory();
        AxeFuserContext context = new AxeFuserContext(
                inventory,
                furnace.getLocation(),
                BloodstoneMenuService.effectAxesByDescendingPrice(rank),
                List.of()
        );
        contexts.put(player.getUniqueId(), context);
        render(player, context);
        player.openInventory(inventory);
        presentationService.playMenuNavigation(player);
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !menuView.matchesTitle(event.getView().title())) {
            return;
        }
        event.setCancelled(true);
        AxeFuserContext context = contexts.get(player.getUniqueId());
        if (context == null) {
            player.closeInventory();
            return;
        }
        if (!hasAxeFuserAccess(BloodstoneRank.resolve(player))) {
            contexts.remove(player.getUniqueId());
            player.closeInventory();
            reject(
                    player,
                    BloodstoneServerConstants.AXE_FUSER_ACCESS_REQUIRED
            );
            return;
        }
        int rawSlot = event.getRawSlot();
        Optional<EffectAxeDefinition> clickedDefinition =
                menuView.clickedDefinition(
                        rawSlot,
                        context.orderedEffects()
                );
        if (clickedDefinition.isPresent()) {
            selectEffect(
                    player,
                    context,
                    clickedDefinition.get()
            );
            return;
        }
        if (rawSlot == AxeFuserMenuView.FUSE_BUTTON_SLOT) {
            beginFusion(player, context);
        }
    }

    public void handleInventoryClose(Player player, Component title) {
        if (menuView.matchesTitle(title)) {
            contexts.remove(player.getUniqueId());
        }
    }

    public void handleInventoryDrag(InventoryDragEvent event) {
        if (menuView.matchesTitle(event.getView().title())) {
            event.setCancelled(true);
        }
    }

    public void handleDisconnect(UUID playerId) {
        contexts.remove(playerId);
    }

    private void selectEffect(
            Player player,
            AxeFuserContext context,
            EffectAxeDefinition selectedEffect
    ) {
        List<EffectAxeDefinition> selections =
                new ArrayList<>(context.selectedEffects());
        if (selections.remove(selectedEffect)) {
            updateContext(player, context, List.copyOf(selections));
            return;
        }
        if (selections.size() >= 2) {
            reject(player, BloodstoneServerConstants.AXE_FUSER_SELECTION_FULL);
            return;
        }
        selections.add(selectedEffect);
        updateContext(player, context, List.copyOf(selections));
    }

    private void updateContext(
            Player player,
            AxeFuserContext previous,
            List<EffectAxeDefinition> selectedEffects
    ) {
        AxeFuserContext updated = new AxeFuserContext(
                previous.menuInventory(),
                previous.blockLocation(),
                previous.orderedEffects(),
                selectedEffects
        );
        contexts.put(player.getUniqueId(), updated);
        render(player, updated);
        presentationService.playMenuNavigation(player);
    }

    private void render(Player player, AxeFuserContext context) {
        Inventory inventory = context.menuInventory();
        if (context.selectedEffects().size() != 2) {
            menuView.render(
                    inventory,
                    context.orderedEffects(),
                    context.selectedEffects(),
                    null,
                    false
            );
            return;
        }
        EffectAxeDefinition firstEffect = context.selectedEffects().get(0);
        EffectAxeDefinition secondEffect = context.selectedEffects().get(1);
        CombinedEffectAxeDefinition combinedDefinition =
                CombinedEffectAxeDefinitions.find(firstEffect, secondEffect)
                        .orElseThrow();
        Optional<OwnedEffectAxe> firstOwned =
                findBestOwnedAxe(player, firstEffect);
        Optional<OwnedEffectAxe> secondOwned =
                findBestOwnedAxe(player, secondEffect);
        if (firstOwned.isEmpty() || secondOwned.isEmpty()) {
            menuView.render(
                    inventory,
                    context.orderedEffects(),
                    context.selectedEffects(),
                    null,
                    true
            );
            return;
        }
        int remainingUses = Math.min(
                firstOwned.get().remainingUses(),
                secondOwned.get().remainingUses()
        );
        menuView.render(
                inventory,
                context.orderedEffects(),
                context.selectedEffects(),
                itemService.createCombinedEffectAxeMenuDisplay(
                        combinedDefinition,
                        remainingUses
                ),
                false
        );
    }

    private void beginFusion(Player player, AxeFuserContext context) {
        if (context.selectedEffects().size() != 2) {
            reject(
                    player,
                    BloodstoneServerConstants.AXE_FUSER_SELECTION_REQUIRED
            );
            return;
        }
        if (!acceptingOperations) {
            reject(player, BloodstoneServerConstants.ERROR_SHUTTING_DOWN);
            return;
        }
        if (combatService.isTagged(player.getUniqueId())) {
            reject(player, BloodstoneServerConstants.ERROR_IN_BATTLE);
            return;
        }
        if (itemService.countBloodAlloy(player.getInventory())
                < FUSION_BLOOD_ALLOY_COST) {
            messageService.sendRequiredCurrency(
                    player,
                    FUSION_BLOOD_ALLOY_COST,
                    BloodstoneMessageService.Currency.BLOOD_ALLOY
            );
            return;
        }
        if (context.blockLocation().getBlock().getType()
                != Material.FURNACE) {
            player.closeInventory();
            reject(
                    player,
                    BloodstoneServerConstants.AXE_FUSER_UNAVAILABLE
            );
            return;
        }
        EffectAxeDefinition firstEffect = context.selectedEffects().get(0);
        EffectAxeDefinition secondEffect = context.selectedEffects().get(1);
        Optional<OwnedEffectAxe> firstOwned =
                findBestOwnedAxe(player, firstEffect);
        Optional<OwnedEffectAxe> secondOwned =
                findBestOwnedAxe(player, secondEffect);
        if (firstOwned.isEmpty() || secondOwned.isEmpty()) {
            reject(player, BloodstoneServerConstants.AXE_FUSER_AXES_REQUIRED);
            return;
        }
        CombinedEffectAxeDefinition combinedDefinition =
                CombinedEffectAxeDefinitions.find(firstEffect, secondEffect)
                        .orElseThrow();
        ItemStack fusedAxe = itemService.createEffectAxe(combinedDefinition);
        itemService.setRemainingUses(
                fusedAxe,
                Math.min(
                        firstOwned.get().remainingUses(),
                        secondOwned.get().remainingUses()
                )
        );
        reserveAndAnimate(
                player,
                context,
                firstOwned.get(),
                secondOwned.get(),
                fusedAxe
        );
    }

    private void reserveAndAnimate(
            Player player,
            AxeFuserContext context,
            OwnedEffectAxe firstOwned,
            OwnedEffectAxe secondOwned,
            ItemStack fusedAxe
    ) {
        UUID operationId = UUID.randomUUID();
        UUID playerId = player.getUniqueId();
        AxeFuserBlockPosition blockPosition =
                AxeFuserBlockPosition.from(context.blockLocation());
        if (!activeFuserBlocks.tryBegin(blockPosition, operationId)) {
            reject(player, BloodstoneServerConstants.MACHINE_ALREADY_ACTIVATED);
            return;
        }
        if (!activeFuserPlayers.tryBegin(playerId, operationId)) {
            activeFuserBlocks.finish(blockPosition, operationId);
            reject(player, BloodstoneServerConstants.MACHINE_ALREADY_ACTIVATED);
            return;
        }

        ItemStack firstOriginal = firstOwned.item().clone();
        ItemStack secondOriginal = secondOwned.item().clone();
        byte[] originalAxesPayload;
        byte[] fusedAxePayload;
        try {
            originalAxesPayload = BukkitItemSerialization.serializeContents(
                    new ItemStack[]{firstOriginal, secondOriginal}
            );
            fusedAxePayload = BukkitItemSerialization.serializeItem(fusedAxe);
        } catch (IOException exception) {
            finishOperation(blockPosition, playerId, operationId);
            logger.log(
                    Level.SEVERE,
                    "Failed to serialize Axe Fuser operation",
                    exception
            );
            return;
        }
        if (!itemService.removeBloodAlloy(
                player.getInventory(),
                FUSION_BLOOD_ALLOY_COST
        )) {
            finishOperation(blockPosition, playerId, operationId);
            messageService.sendRequiredCurrency(
                    player,
                    FUSION_BLOOD_ALLOY_COST,
                    BloodstoneMessageService.Currency.BLOOD_ALLOY
            );
            return;
        }

        contexts.remove(playerId);
        player.closeInventory();
        UUID firstMarker =
                AxeFuserOperation.reservedItemMarker(operationId, 0);
        UUID secondMarker =
                AxeFuserOperation.reservedItemMarker(operationId, 1);
        player.getInventory().setItem(
                firstOwned.slot(),
                itemService.withOperationId(firstOwned.item(), firstMarker)
        );
        player.getInventory().setItem(
                secondOwned.slot(),
                itemService.withOperationId(secondOwned.item(), secondMarker)
        );

        reserveWithRetry(
                operationId,
                playerId,
                originalAxesPayload,
                FUSION_BLOOD_ALLOY_COST,
                Instant.now()
        ).thenAcceptAsync(outcome -> finishReservation(
                        player,
                        context.blockLocation(),
                        blockPosition,
                        firstOwned.slot(),
                        secondOwned.slot(),
                        firstOriginal,
                        secondOriginal,
                        fusedAxe,
                        fusedAxePayload,
                        operationId,
                        outcome
                ), mainThreadExecutor)
                .exceptionally(exception -> {
                    mainThreadExecutor.execute(() -> {
                        finishOperation(blockPosition, playerId, operationId);
                        restoreTaggedInput(player, firstMarker, firstOriginal);
                        restoreTaggedInput(player, secondMarker, secondOriginal);
                        refundFusionCost(player, operationId);
                        if (player.isOnline()) {
                            reject(
                                    player,
                                    BloodstoneServerConstants
                                            .AXE_FUSER_RESERVATION_REFUNDED
                            );
                        }
                    });
                    logger.log(
                            Level.SEVERE,
                            "Failed to reserve Axe Fuser operation",
                            exception
                    );
                    return null;
                });
    }

    private CompletableFuture<AxeFuserReserveOutcome> reserveWithRetry(
            UUID operationId,
            UUID playerId,
            byte[] originalAxesPayload,
            int bloodAlloyCost,
            Instant startedAt
    ) {
        return storage.reserveAxeFuserOperation(
                operationId,
                playerId,
                originalAxesPayload,
                bloodAlloyCost,
                startedAt
        ).exceptionallyCompose(exception -> {
            if (!acceptingOperations) {
                return CompletableFuture.failedFuture(exception);
            }
            logger.log(
                    Level.WARNING,
                    "Retrying Axe Fuser reservation " + operationId,
                    exception
            );
            return CompletableFuture.supplyAsync(
                    () -> null,
                    CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
            ).thenCompose(ignored -> reserveWithRetry(
                    operationId,
                    playerId,
                    originalAxesPayload,
                    bloodAlloyCost,
                    startedAt
            ));
        });
    }

    private void finishReservation(
            Player player,
            Location blockLocation,
            AxeFuserBlockPosition blockPosition,
            int firstSlot,
            int secondSlot,
            ItemStack firstOriginal,
            ItemStack secondOriginal,
            ItemStack fusedAxe,
            byte[] fusedAxePayload,
            UUID operationId,
            AxeFuserReserveOutcome outcome
    ) {
        UUID playerId = player.getUniqueId();
        if (!(outcome instanceof AxeFuserReserveOutcome.Reserved)) {
            finishOperation(blockPosition, playerId, operationId);
            throw new IllegalStateException(
                    "Unsupported Axe Fuser reservation outcome"
            );
        }
        if (!player.isOnline()) {
            finishOperation(blockPosition, playerId, operationId);
            return;
        }
        UUID firstMarker =
                AxeFuserOperation.reservedItemMarker(operationId, 0);
        UUID secondMarker =
                AxeFuserOperation.reservedItemMarker(operationId, 1);
        if (!hasMarker(player, firstSlot, firstMarker)
                || !hasMarker(player, secondSlot, secondMarker)) {
            finishOperation(blockPosition, playerId, operationId);
            reject(
                    player,
                    BloodstoneServerConstants.AXE_FUSER_INPUT_RECOVERY
            );
            playerService.recoverAxeFuserOperation(player, operationId);
            return;
        }
        player.getInventory().clear(firstSlot);
        player.getInventory().clear(secondSlot);
        storage.markAxeFuserOperationReady(
                operationId,
                playerId,
                fusedAxePayload
        ).thenAcceptAsync(ready -> {
            if (!ready) {
                throw new IllegalStateException(
                        "Axe Fuser operation disappeared before becoming ready"
                );
            }
            animation.play(
                    player,
                    blockLocation,
                    firstOriginal,
                    secondOriginal,
                    fusedAxe,
                    operationId,
                    () -> finishOperation(
                            blockPosition,
                            playerId,
                            operationId
                    ),
                    () -> playerService.recoverAxeFuserOperation(
                            player,
                            operationId
                    )
            );
        }, mainThreadExecutor).exceptionally(exception -> {
            logger.log(
                    Level.SEVERE,
                    "Failed to prepare Axe Fuser operation",
                    exception
            );
            mainThreadExecutor.execute(() -> {
                finishOperation(blockPosition, playerId, operationId);
                if (player.isOnline()) {
                    playerService.recoverAxeFuserOperation(
                            player,
                            operationId
                    );
                }
            });
            return null;
        });
    }

    private Optional<OwnedEffectAxe> findBestOwnedAxe(
            Player player,
            EffectAxeDefinition definition
    ) {
        List<OwnedEffectAxe> matches = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null
                    || item.getType() == Material.AIR
                    || itemService.operationId(item).isPresent()) {
                continue;
            }
            Optional<EffectAxeDefinition> ownedDefinition =
                    itemService.baseEffectAxeDefinition(item);
            if (ownedDefinition.filter(definition::equals).isPresent()) {
                matches.add(new OwnedEffectAxe(
                        slot,
                        item,
                        itemService.remainingUses(item)
                ));
            }
        }
        return matches.stream().max(
                Comparator.comparingInt(OwnedEffectAxe::remainingUses)
                        .thenComparing(
                                Comparator.comparingInt(OwnedEffectAxe::slot)
                                        .reversed()
                        )
        );
    }

    private boolean hasMarker(Player player, int slot, UUID marker) {
        ItemStack item = player.getInventory().getItem(slot);
        return item != null
                && itemService.operationId(item).filter(marker::equals).isPresent();
    }

    private void restoreTaggedInput(
            Player player,
            UUID marker,
            ItemStack original
    ) {
        if (!player.isOnline()) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null
                    && itemService.operationId(item)
                    .filter(marker::equals)
                    .isPresent()) {
                player.getInventory().setItem(slot, original.clone());
                return;
            }
        }
    }

    private void refundFusionCost(Player player, UUID operationId) {
        if (!player.isOnline()) {
            return;
        }
        ItemStack refund = itemService.withOperationId(
                itemService.createBloodAlloy(FUSION_BLOOD_ALLOY_COST),
                AxeFuserOperation.reservedItemMarker(operationId, 2)
        );
        Map<Integer, ItemStack> leftovers =
                player.getInventory().addItem(refund);
        leftovers.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(
                        player.getLocation(),
                        leftover
                )
        );
    }

    static boolean hasAxeFuserAccess(BloodstoneRank rank) {
        return Objects.requireNonNull(
                rank,
                "Bloodstone rank cannot be null"
        ) == BloodstoneRank.ARCHON;
    }

    private void finishOperation(
            AxeFuserBlockPosition blockPosition,
            UUID playerId,
            UUID operationId
    ) {
        activeFuserBlocks.finish(blockPosition, operationId);
        activeFuserPlayers.finish(playerId, operationId);
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }

    public void shutdown() {
        acceptingOperations = false;
        contexts.clear();
        animation.shutdown();
        activeFuserBlocks.clear();
        activeFuserPlayers.clear();
    }

    private record AxeFuserContext(
            Inventory menuInventory,
            Location blockLocation,
            List<EffectAxeDefinition> orderedEffects,
            List<EffectAxeDefinition> selectedEffects
    ) {

        private AxeFuserContext {
            blockLocation = blockLocation.clone();
            orderedEffects = List.copyOf(orderedEffects);
            selectedEffects = List.copyOf(selectedEffects);
        }

        @Override
        public Location blockLocation() {
            return blockLocation.clone();
        }
    }

    private record OwnedEffectAxe(
            int slot,
            ItemStack item,
            int remainingUses
    ) {
    }

    private record AxeFuserBlockPosition(
            UUID worldId,
            int blockX,
            int blockY,
            int blockZ
    ) {

        private static AxeFuserBlockPosition from(Location location) {
            return new AxeFuserBlockPosition(
                    location.getWorld().getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
        }
    }
}
