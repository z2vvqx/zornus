package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import net.valoury.bloodstone.server.storage.EnchanterReserveOutcome;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstoneEnchanterService {

    private static final int MAXIMUM_CONCURRENT_OPERATIONS = 4;

    private final Plugin plugin;
    private final BloodstoneStorage storage;
    private final BloodstoneItemService itemService;
    private final BloodstoneCombatService combatService;
    private final BloodstonePlayerService playerService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMessageService messageService;
    private final Logger logger;

    private final Map<UUID, EnchanterContext> contexts = new HashMap<>();
    private final OperationCapacity activeOperations = new OperationCapacity(
            MAXIMUM_CONCURRENT_OPERATIONS
    );
    private final Set<Item> animationDisplays = new java.util.HashSet<>();
    private volatile boolean acceptingOperations = true;

    public BloodstoneEnchanterService(
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
        this.plugin = plugin;
        this.storage = storage;
        this.itemService = itemService;
        this.combatService = combatService;
        this.playerService = playerService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.presentationService = presentationService;
        this.messageService = messageService;
        this.logger = logger;
    }

    public void handleDisconnect(UUID playerId) {
        contexts.remove(playerId);
    }

    public void openRankEnchanter(Player player, Block block) {
        openRankEnchantmentTool(
                player,
                block,
                EnchantmentToolAction.ENCHANT
        );
    }

    public void openRankDisenchanter(Player player, Block block) {
        openRankEnchantmentTool(
                player,
                block,
                EnchantmentToolAction.DISENCHANT
        );
    }

    private void openRankEnchantmentTool(
            Player player,
            Block block,
            EnchantmentToolAction action
    ) {
        if (!acceptingOperations) {
            reject(player, BloodstoneServerConstants.ENCHANTER_SHUTTING_DOWN);
            return;
        }
        BloodstoneRank rank = BloodstoneRank.resolve(player);
        if (!rank.isPaid()) {
            reject(player, action.accessRequiredMessage());
            return;
        }
        if (combatService.isTagged(player.getUniqueId())) {
            reject(player, BloodstoneServerConstants.ERROR_IN_BATTLE);
            return;
        }
        if (!activeOperations.hasAvailability()) {
            reject(player, BloodstoneServerConstants.ENCHANTER_CAPACITY_REACHED);
            return;
        }
        ItemStack heldItem = player.getItemInHand();
        if (heldItem == null
                || !EnchantmentToolCatalog.supports(heldItem.getType())) {
            reject(player, BloodstoneServerConstants.ERROR_UNRECOGNIZED_ITEM);
            return;
        }
        if (itemService.isExclusive(heldItem)) {
            reject(player, action.itemRejectedMessage());
            return;
        }
        if (itemService.isSoulbound(heldItem)) {
            reject(player, BloodstoneServerConstants.ENCHANTER_ITEM_TOO_POWERFUL);
            return;
        }

        List<EnchantmentToolCatalog.Option> options =
                EnchantmentToolCatalog.optionsFor(heldItem.getType());
        Inventory inventory = Bukkit.createInventory(
                null,
                27,
                action.menuTitle()
        );
        for (EnchantmentToolCatalog.Option option : options) {
            inventory.setItem(option.slot(), option.displayItem(action));
        }
        contexts.put(player.getUniqueId(), new EnchanterContext(
                inventory,
                block.getLocation(),
                player.getInventory().getHeldItemSlot(),
                heldItem.clone(),
                rank,
                action,
                options
        ));
        player.openInventory(inventory);
        presentationService.playMenuNavigation(player);
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !isEnchantmentToolMenu(event.getView().title())) {
            return;
        }
        event.setCancelled(true);
        EnchanterContext context = contexts.get(player.getUniqueId());
        if (context == null
                || !context.action().menuTitle().equals(event.getView().title())) {
            player.closeInventory();
            return;
        }
        EnchantmentToolCatalog.Option option = context.options().stream()
                .filter(candidate -> candidate.slot() == event.getRawSlot())
                .findFirst()
                .orElse(null);
        if (option == null) {
            return;
        }
        applyOption(player, context, option);
    }

    public void handleInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !(event.getInventory() instanceof EnchantingInventory enchantingInventory)
                || !isBloodstone(player)) {
            return;
        }
        enchantingInventory.setSecondary(itemService.createArtificialLapis(64));
    }

    public void handleNormalEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (!isBloodstone(player)) {
            return;
        }
        if (combatService.isTagged(player.getUniqueId())) {
            event.setCancelled(true);
            reject(player, BloodstoneServerConstants.ERROR_IN_BATTLE);
            return;
        }
        ItemStack item = event.getItem();
        boolean removeClassification = itemService.isInclusive(item) || itemService.isExclusive(item);
        player.playSound(player.getLocation(), Sound.ZOMBIE_UNFECT, 1.0F, 1.55F);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getInventory() instanceof EnchantingInventory enchantingInventory) {
                if (removeClassification && enchantingInventory.getItem() != null) {
                    enchantingInventory.setItem(
                            itemService.removeClassification(enchantingInventory.getItem()));
                }
                enchantingInventory.setSecondary(itemService.createArtificialLapis(64));
            }
        });
    }

    public void handleInventoryClickForLapis(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isBloodstone(player)) {
            return;
        }
        if (event.getView().getTopInventory() instanceof EnchantingInventory
                && (event.getRawSlot() == 1
                || itemService.isArtificialLapis(event.getCurrentItem())
                || itemService.isArtificialLapis(event.getCursor()))) {
            event.setCancelled(true);
        }
    }

    public void handleArtificialLapisDrop(PlayerDropItemEvent event) {
        if (isBloodstone(event.getPlayer())
                && itemService.isArtificialLapis(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    public void handleInventoryClose(Player player, Inventory inventory) {
        if (inventory instanceof EnchantingInventory enchantingInventory
                && itemService.isArtificialLapis(enchantingInventory.getSecondary())) {
            enchantingInventory.setSecondary(null);
        }
        EnchanterContext context = contexts.get(player.getUniqueId());
        if (context != null && context.menuInventory() == inventory) {
            contexts.remove(player.getUniqueId(), context);
        }
    }

    private void applyOption(
            Player player,
            EnchanterContext context,
            EnchantmentToolCatalog.Option option
    ) {
        ItemStack current = player.getInventory().getItem(context.heldSlot());
        if (current == null || !current.equals(context.originalItem())) {
            player.closeInventory();
            reject(player, context.action().heldItemChangedMessage());
            return;
        }
        int currentLevel = current.getEnchantmentLevel(option.enchantment());
        if (!context.action().isSelectionAvailable(currentLevel, option.level())) {
            reject(
                    player,
                    context.action().unavailableSelectionMessage()
            );
            return;
        }
        Duration cooldown = context.rank().enchanterCooldown().orElseThrow();
        if (!activeOperations.hasAvailability()) {
            player.closeInventory();
            reject(player, BloodstoneServerConstants.ENCHANTER_CAPACITY_REACHED);
            return;
        }
        player.closeInventory();
        reserveAndAnimate(player, context, option, cooldown);
    }

    private void reserveAndAnimate(
            Player player,
            EnchanterContext context,
            EnchantmentToolCatalog.Option option,
            Duration cooldown
    ) {
        ItemStack current = player.getInventory().getItem(context.heldSlot());
        if (current == null || !current.equals(context.originalItem()) || !player.isOnline()) {
            reject(player, context.action().heldItemChangedMessage());
            return;
        }
        ItemStack resultItem = context.action().transform(
                current,
                option.enchantment(),
                option.level()
        );
        UUID operationId = UUID.randomUUID();
        byte[] originalPayload;
        byte[] resultPayload;
        try {
            originalPayload = BukkitItemSerialization.serializeItem(current);
            resultPayload = BukkitItemSerialization.serializeItem(resultItem);
        } catch (IOException exception) {
            logger.log(
                    Level.SEVERE,
                    "Failed to serialize enchantment tool operation",
                    exception
            );
            return;
        }
        if (!activeOperations.tryBegin(operationId)) {
            reject(player, BloodstoneServerConstants.ENCHANTER_CAPACITY_REACHED);
            return;
        }
        player.getInventory().setItem(
                context.heldSlot(),
                itemService.withOperationId(current, operationId)
        );
        reserveEnchanterWithRetry(
                operationId,
                player.getUniqueId(),
                context.action().offerKey(option.offerKey()),
                Instant.now(),
                cooldown,
                originalPayload
        )
                .thenAcceptAsync(outcome -> finishEnchanterReservation(
                        player,
                        context.heldSlot(),
                        context.blockLocation(),
                        current.clone(),
                        resultItem,
                        resultPayload,
                        context.action(),
                        operationId,
                        outcome
                ), mainThreadExecutor)
                .exceptionally(exception -> {
                    mainThreadExecutor.execute(() -> {
                        activeOperations.finish(operationId);
                        restoreTaggedOriginal(
                                player,
                                context.heldSlot(),
                                operationId,
                                current
                        );
                    });
                    logger.log(
                            Level.SEVERE,
                            "Failed to reserve Bloodstone enchantment tool operation",
                            exception
                    );
                    return null;
                });
    }

    private CompletableFuture<EnchanterReserveOutcome> reserveEnchanterWithRetry(
            UUID operationId,
            UUID playerId,
            String offerKey,
            Instant startedAt,
            Duration cooldown,
            byte[] originalPayload
    ) {
        return storage.reserveEnchanterOperation(
                operationId,
                playerId,
                offerKey,
                startedAt,
                cooldown,
                originalPayload
        ).exceptionallyCompose(exception -> {
            if (!acceptingOperations) {
                return CompletableFuture.failedFuture(exception);
            }
            logger.log(
                    Level.WARNING,
                    "Retrying Bloodstone enchantment tool reservation "
                            + operationId,
                    exception
            );
            return CompletableFuture.supplyAsync(
                    () -> null,
                    CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
            ).thenCompose(ignored -> reserveEnchanterWithRetry(
                    operationId,
                    playerId,
                    offerKey,
                    startedAt,
                    cooldown,
                    originalPayload
            ));
        });
    }

    private void finishEnchanterReservation(
            Player player,
            int heldSlot,
            Location blockLocation,
            ItemStack original,
            ItemStack resultItem,
            byte[] resultPayload,
            EnchantmentToolAction action,
            UUID operationId,
            EnchanterReserveOutcome outcome
    ) {
        if (!player.isOnline()) {
            activeOperations.finish(operationId);
            return;
        }
        if (outcome instanceof EnchanterReserveOutcome.OnCooldown cooldown) {
            activeOperations.finish(operationId);
            restoreTaggedOriginal(player, heldSlot, operationId, original);
            messageService.sendUnable(
                    player,
                    action.cooldownErrorKey(),
                    BloodstoneServerConstants.ENCHANTER_COOLDOWN_FORMAT,
                    Placeholder.component(
                            "cooldown",
                            StringUtils.formatRelativeTime(cooldown.availableAt())
                    )
            );
            return;
        }

        ItemStack heldItem = player.getInventory().getItem(heldSlot);
        if (heldItem == null
                || itemService.operationId(heldItem).filter(operationId::equals).isEmpty()) {
            activeOperations.finish(operationId);
            reject(player, action.heldItemRecoveryMessage());
            playerService.recoverEnchanterOperation(player, operationId);
            return;
        }
        player.getInventory().clear(heldSlot);
        storage.markEnchanterOperationReady(operationId, player.getUniqueId(), resultPayload)
                .thenAcceptAsync(ready -> {
                    if (!ready) {
                        activeOperations.finish(operationId);
                        playerService.recoverEnchanterOperation(player, operationId);
                        throw new IllegalStateException(
                                "Enchantment tool operation disappeared before becoming ready"
                        );
                    }
                    playAnimation(
                            player,
                            blockLocation,
                            original,
                            resultItem,
                            operationId
                    );
                }, mainThreadExecutor)
                .exceptionally(exception -> {
                    logger.log(
                            Level.SEVERE,
                            "Failed to prepare Bloodstone enchantment tool operation",
                            exception
                    );
                    mainThreadExecutor.execute(() -> {
                        activeOperations.finish(operationId);
                        if (player.isOnline()) {
                            playerService.recoverEnchanterOperation(player, operationId);
                        }
                    });
                    return null;
                });
    }

    private void restoreTaggedOriginal(
            Player player,
            int heldSlot,
            UUID operationId,
            ItemStack original
    ) {
        ItemStack current = player.getInventory().getItem(heldSlot);
        if (current != null
                && itemService.operationId(current).filter(operationId::equals).isPresent()) {
            player.getInventory().setItem(heldSlot, original);
        }
    }

    private void playAnimation(
            Player player,
            Location blockLocation,
            ItemStack original,
            ItemStack resultItem,
            UUID operationId
    ) {
        Item display = blockLocation.getWorld().dropItem(
                blockLocation.clone().add(0.5, 1.25, 0.5),
                original
        );
        animationDisplays.add(display);
        display.setPickupDelay(Integer.MAX_VALUE);
        display.setVelocity(new Vector());
        for (int step = 1; step <= 6; step++) {
            int animationStep = step;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!display.isDead()) {
                    if (animationStep == 4) {
                        display.setItemStack(resultItem.clone());
                    }
                    display.getWorld().spigot().playEffect(
                            display.getLocation(),
                            Effect.WITCH_MAGIC,
                            0,
                            0,
                            0.15F,
                            0.25F,
                            0.15F,
                            0.05F,
                            6,
                            48
                    );
                    float pitch = (float) java.util.concurrent.ThreadLocalRandom.current()
                            .nextDouble(1.8D, 2.0D);
                    display.getWorld().playSound(
                            display.getLocation(), Sound.ANVIL_LAND, 0.5F, pitch);
                    display.getWorld().playSound(
                            display.getLocation(), Sound.ENDERMAN_TELEPORT, 0.5F, pitch);
                    display.getWorld().playSound(
                            display.getLocation(), Sound.DIG_STONE, 0.5F, pitch);
                    display.getWorld().playSound(
                            display.getLocation(), Sound.ZOMBIE_WOOD, 0.5F, pitch);
                }
            }, step * 8L);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            animationDisplays.remove(display);
            display.remove();
            if (!player.isOnline()) {
                activeOperations.finish(operationId);
                return;
            }
            playerService.deliverReservedItem(
                            player,
                            operationId,
                            resultItem,
                            false,
                            () -> storage.completeEnchanterOperation(
                                    operationId,
                                    player.getUniqueId()
                            )
                    )
                    .thenAcceptAsync(outcome -> {
                        activeOperations.finish(operationId);
                        if (outcome.wasDelivered()
                                && player.isOnline()) {
                            player.playSound(
                                    player.getLocation(),
                                    Sound.ENDERMAN_TELEPORT,
                                    0.5F,
                                    2.0F
                            );
                        }
                    }, mainThreadExecutor)
                    .exceptionally(exception -> {
                        logger.log(
                                Level.SEVERE,
                                "Failed to complete enchantment tool item delivery",
                                exception
                        );
                        mainThreadExecutor.execute(() -> {
                            activeOperations.finish(operationId);
                            if (player.isOnline()) {
                                playerService.recoverEnchanterOperation(player, operationId);
                            }
                        });
                        return null;
                    });
        }, 50L);
    }

    public void shutdown() {
        acceptingOperations = false;
        contexts.clear();
        animationDisplays.forEach(Item::remove);
        animationDisplays.clear();
        activeOperations.clear();
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }

    private boolean isBloodstone(Player player) {
        return "bloodstone".equals(player.getWorld().getName());
    }

    private boolean isEnchantmentToolMenu(Component title) {
        return EnchantmentToolAction.isMenuTitle(title);
    }

    private record EnchanterContext(
            Inventory menuInventory,
            Location blockLocation,
            int heldSlot,
            ItemStack originalItem,
            BloodstoneRank rank,
            EnchantmentToolAction action,
            List<EnchantmentToolCatalog.Option> options
    ) {
    }
}
