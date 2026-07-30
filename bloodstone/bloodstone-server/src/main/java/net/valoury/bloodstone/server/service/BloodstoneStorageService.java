package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.model.StorageSession;
import net.valoury.bloodstone.server.model.StorageType;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import net.valoury.bloodstone.server.storage.ExtraStorageUnlockOutcome;
import net.valoury.bloodstone.server.storage.StorageOpenOutcome;
import net.valoury.bloodstone.server.storage.StorageWriteOutcome;
import net.valoury.shared.utilities.StringUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BloodstoneStorageService {

    private static final Duration STORAGE_LEASE_DURATION = Duration.ofSeconds(30);
    private static final int STORAGE_CLOSE_MAXIMUM_ATTEMPTS = 3;
    private static final long STORAGE_RETRY_DELAY_SECONDS = 1L;
    private static final int EXTRA_STORAGE_PRICE = 8;
    private static final Component STORAGE_MENU_TITLE =
            BloodstoneText.deserialize(BloodstoneServerConstants.STORAGE_MENU_TITLE);
    private final BloodstoneStorage storage;
    private final BloodstoneItemService itemService;
    private final BloodstonePlayerService playerService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMessageService messageService;
    private final Logger logger;

    private final Map<UUID, ActiveStorage> activeStorages = new HashMap<>();
    private final Map<UUID, ActiveStorage> closingStorages = new HashMap<>();
    private final Map<UUID, UUID> pendingOpenTokens = new HashMap<>();
    private final Set<UUID> locallyUnlockedExtraStorage = new HashSet<>();
    private final Set<UUID> pendingExtraStoragePurchases = new HashSet<>();
    private volatile boolean acceptingOperations = true;
    private volatile boolean retryingFailedSaves = true;

    public BloodstoneStorageService(
            BloodstoneStorage storage,
            BloodstoneItemService itemService,
            BloodstonePlayerService playerService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstonePresentationService presentationService,
            BloodstoneMessageService messageService,
            Logger logger
    ) {
        this.storage = storage;
        this.itemService = itemService;
        this.playerService = playerService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.presentationService = presentationService;
        this.messageService = messageService;
        this.logger = logger;
    }

    public void openStorageMenu(Player player) {
        if (!acceptingOperations) {
            reject(player, BloodstoneServerConstants.STORAGE_SHUTTING_DOWN);
            return;
        }
        Inventory menu = Bukkit.createInventory(
                null,
                BloodstoneServerConstants.STORAGE_MENU_ROWS * 9,
                STORAGE_MENU_TITLE
        );
        BloodstoneRank rank = BloodstoneRank.resolve(player);
        menu.setItem(
                BloodstoneServerConstants.DEFAULT_STORAGE_SLOT,
                storageButton(StorageType.DEFAULT, true)
        );
        menu.setItem(
                BloodstoneServerConstants.LEGATE_STORAGE_SLOT,
                storageButton(
                        StorageType.LEGATE,
                        rank.ordinal() >= BloodstoneRank.LEGATE.ordinal()
                )
        );
        menu.setItem(
                BloodstoneServerConstants.JUSTICAR_STORAGE_SLOT,
                storageButton(
                        StorageType.JUSTICAR,
                        rank.ordinal() >= BloodstoneRank.JUSTICAR.ordinal()
                )
        );
        menu.setItem(
                BloodstoneServerConstants.REGENT_STORAGE_SLOT,
                storageButton(
                        StorageType.REGENT,
                        rank.ordinal() >= BloodstoneRank.REGENT.ordinal()
                )
        );
        menu.setItem(
                BloodstoneServerConstants.ARCHON_STORAGE_SLOT,
                storageButton(StorageType.ARCHON, rank == BloodstoneRank.ARCHON)
        );
        boolean extraUnlocked = isExtraUnlocked(player.getUniqueId());
        menu.setItem(
                BloodstoneServerConstants.EXTRA_STORAGE_SLOT,
                storageButton(StorageType.EXTRA, extraUnlocked)
        );
        menu.setItem(
                BloodstoneServerConstants.GUILD_STASH_SLOT,
                BloodstoneServerConstants.GUILD_STASH_ITEM.create()
        );
        player.openInventory(menu);
        presentationService.playMenuNavigation(player);
        player.getWorld().spigot().playEffect(
                player.getLocation(),
                org.bukkit.Effect.PORTAL,
                0,
                0,
                0.35F,
                0.5F,
                0.35F,
                0.08F,
                16,
                32
        );
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (STORAGE_MENU_TITLE.equals(event.getView().title())) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                return;
            }
            StorageType type = switch (event.getRawSlot()) {
                case BloodstoneServerConstants.DEFAULT_STORAGE_SLOT -> StorageType.DEFAULT;
                case BloodstoneServerConstants.LEGATE_STORAGE_SLOT -> StorageType.LEGATE;
                case BloodstoneServerConstants.JUSTICAR_STORAGE_SLOT -> StorageType.JUSTICAR;
                case BloodstoneServerConstants.REGENT_STORAGE_SLOT -> StorageType.REGENT;
                case BloodstoneServerConstants.ARCHON_STORAGE_SLOT -> StorageType.ARCHON;
                case BloodstoneServerConstants.EXTRA_STORAGE_SLOT -> StorageType.EXTRA;
                default -> null;
            };
            if (type == null) {
                return;
            }
            if (type == StorageType.EXTRA && !isExtraUnlocked(player.getUniqueId())) {
                purchaseExtraStorage(player);
                return;
            }
            if (!isEligible(player, type)) {
                reject(player, BloodstoneServerConstants.STORAGE_LOCKED);
                return;
            }
            beginOpen(player, type);
        }
    }

    public void handleInventoryClose(Player player, Inventory inventory) {
        ActiveStorage active = activeStorages.get(player.getUniqueId());
        if (active != null && isOwnedInventory(inventory, active)) {
            closeActiveStorage(player.getUniqueId(), active, inventory, true);
        }
    }

    static boolean isOwnedInventory(
            @NonNull Inventory inventory,
            @NonNull InventoryHolder expectedHolder
    ) {
        return inventory.getHolder() == expectedHolder;
    }

    public CompletableFuture<?> handleQuit(Player player) {
        pendingOpenTokens.remove(player.getUniqueId());
        ActiveStorage active = activeStorages.get(player.getUniqueId());
        if (active == null) {
            return CompletableFuture.completedFuture(null);
        }
        return closeActiveStorage(player.getUniqueId(), active, active.inventory, false);
    }

    public boolean isPurchasePending(UUID playerId) {
        return pendingExtraStoragePurchases.contains(playerId);
    }

    public void checkpointActiveStorages() {
        for (Map.Entry<UUID, ActiveStorage> entry : new ArrayList<>(activeStorages.entrySet())) {
            ActiveStorage active = entry.getValue();
            byte[] payload = snapshot(active.inventory);
            active.tail = continueFromLastCommitted(active).thenCompose(session ->
                    checkpointStorageWithRetry(
                                    session,
                                    payload,
                                    1
                            )
                            .thenCompose(outcome -> mapWriteOutcome(entry.getKey(), active, outcome)))
                    .thenApply(active::remember);
        }
    }

    public CompletableFuture<Void> shutdown() {
        acceptingOperations = false;
        pendingOpenTokens.clear();
        List<CompletableFuture<?>> saves = new ArrayList<>();
        for (Map.Entry<UUID, ActiveStorage> entry : new ArrayList<>(activeStorages.entrySet())) {
            saves.add(closeActiveStorage(entry.getKey(), entry.getValue(),
                    entry.getValue().inventory, false));
        }
        saves.addAll(closingStorages.values().stream()
                .map(active -> active.tail)
                .toList());
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
    }

    public void stopRetries() {
        retryingFailedSaves = false;
    }

    private void beginOpen(Player player, StorageType storageType) {
        if (!acceptingOperations) {
            reject(player, BloodstoneServerConstants.STORAGE_SHUTTING_DOWN);
            return;
        }
        UUID playerId = player.getUniqueId();
        if (activeStorages.containsKey(playerId) || pendingOpenTokens.containsKey(playerId)) {
            reject(player, BloodstoneServerConstants.STORAGE_OPERATION_IN_PROGRESS);
            return;
        }
        UUID sessionToken = UUID.randomUUID();
        pendingOpenTokens.put(playerId, sessionToken);
        ActiveStorage closingStorage = closingStorages.get(playerId);
        if (closingStorage != null) {
            closingStorage.tail.whenCompleteAsync((ignored, exception) -> {
                if (!sessionToken.equals(pendingOpenTokens.get(playerId))) {
                    return;
                }
                Player current = Bukkit.getPlayer(playerId);
                if (exception != null || current == null || !current.isOnline()
                        || !acceptingOperations) {
                    pendingOpenTokens.remove(playerId, sessionToken);
                    if (exception != null) {
                        logger.log(Level.SEVERE,
                                "Failed to reopen Bloodstone storage after saving for " + playerId,
                                exception);
                        if (current != null && current.isOnline()) {
                            reject(
                                    current,
                                    BloodstoneServerConstants.STORAGE_PREVIOUS_SAVE_FAILED
                            );
                        }
                    }
                    return;
                }
                openStorage(playerId, storageType, sessionToken);
            }, mainThreadExecutor);
            return;
        }
        openStorage(playerId, storageType, sessionToken);
    }

    private void openStorage(UUID playerId, StorageType storageType, UUID sessionToken) {
        storage.openStorage(
                        playerId,
                        storageType,
                        sessionToken,
                        Instant.now(),
                        STORAGE_LEASE_DURATION
                )
                .thenAcceptAsync(outcome -> finishOpen(playerId, sessionToken, storageType, outcome),
                        mainThreadExecutor)
                .exceptionally(exception -> {
                    mainThreadExecutor.execute(() -> pendingOpenTokens.remove(playerId, sessionToken));
                    logger.log(Level.SEVERE, "Failed to open Bloodstone storage for " + playerId, exception);
                    return null;
                });
    }

    private void finishOpen(
            UUID playerId,
            UUID sessionToken,
            StorageType storageType,
            StorageOpenOutcome outcome
    ) {
        if (!pendingOpenTokens.remove(playerId, sessionToken)) {
            if (outcome instanceof StorageOpenOutcome.Opened opened) {
                storage.closeStorage(opened.session(), opened.session().contentsPayload(), Instant.now());
            }
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            if (outcome instanceof StorageOpenOutcome.Opened opened) {
                storage.closeStorage(opened.session(), opened.session().contentsPayload(), Instant.now());
            }
            return;
        }
        if (outcome instanceof StorageOpenOutcome.InUse inUse) {
            messageService.sendError(
                    player,
                    BloodstoneServerConstants.STORAGE_IN_USE_ERROR_KEY,
                    BloodstoneServerConstants.STORAGE_IN_USE_FORMAT,
                    Placeholder.component(
                            "expiration",
                            StringUtils.formatRelativeTime(inUse.leaseExpiresAt())
                    )
            );
            return;
        }
        if (outcome instanceof StorageOpenOutcome.Locked) {
            reject(player, BloodstoneServerConstants.STORAGE_LOCKED);
            return;
        }
        if (outcome instanceof StorageOpenOutcome.PlayerNotFound) {
            reject(player, BloodstoneServerConstants.STORAGE_PROFILE_LOADING);
            return;
        }
        StorageSession session = ((StorageOpenOutcome.Opened) outcome).session();
        ActiveStorage active = new ActiveStorage(
                session,
                storageType.inventorySize(),
                storageTitle(storageType)
        );
        Inventory inventory = active.inventory;
        if (session.contentsPayload() != null) {
            try {
                ItemStack[] contents = BukkitItemSerialization.deserializeContents(session.contentsPayload());
                if (contents.length != inventory.getSize()) {
                    throw new IOException("Persisted storage size does not match " + storageType);
                }
                inventory.setContents(contents);
            } catch (IOException exception) {
                logger.log(Level.SEVERE, "Failed to deserialize " + storageType + " storage for " + playerId,
                        exception);
                storage.closeStorage(session, session.contentsPayload(), Instant.now());
                reject(player, BloodstoneServerConstants.STORAGE_LOAD_FAILED);
                return;
            }
        }
        activeStorages.put(playerId, active);
        player.openInventory(inventory);
        presentationService.playMenuNavigation(player);
    }

    private CompletableFuture<?> closeActiveStorage(
            UUID playerId,
            ActiveStorage active,
            Inventory inventory,
            boolean notify
    ) {
        try {
            byte[] payload = snapshot(inventory);
            return closeActiveStorage(playerId, active, ignored -> payload, notify);
        } catch (IllegalStateException exception) {
            logger.log(Level.SEVERE,
                    "Failed to serialize Bloodstone storage while closing for " + playerId,
                    exception);
            if (notify) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    reject(player, BloodstoneServerConstants.STORAGE_PREVIOUS_SAVE_FAILED);
                }
            }
            return closeActiveStorage(
                    playerId,
                    active,
                    StorageSession::contentsPayload,
                    false
            );
        }
    }

    private CompletableFuture<?> closeActiveStorage(
            UUID playerId,
            ActiveStorage active,
            Function<StorageSession, byte[]> finalPayload,
            boolean notify
    ) {
        if (!activeStorages.remove(playerId, active)) {
            return active.tail;
        }
        closingStorages.put(playerId, active);
        active.tail = continueFromLastCommitted(active).thenCompose(session ->
                closeStorageWithRetry(session, finalPayload.apply(session), 1)
                        .thenCompose(outcome -> mapWriteOutcome(playerId, active, outcome)))
                .thenApply(active::remember);
        active.tail.whenComplete((ignored, exception) -> {
            if (!acceptingOperations) {
                return;
            }
            mainThreadExecutor.executeIfEnabled(() -> {
                closingStorages.remove(playerId, active);
                if (exception != null) {
                    logger.log(
                            Level.SEVERE,
                            "Failed to close Bloodstone storage for " + playerId,
                            exception
                    );
                    return;
                }
            });
        });
        return active.tail;
    }

    private CompletableFuture<StorageSession> continueFromLastCommitted(ActiveStorage active) {
        return active.tail.handle((session, exception) -> {
            if (exception == null) {
                return session;
            }
            Throwable cause = exception instanceof CompletionException completionException
                    ? completionException.getCause()
                    : exception;
            if (cause instanceof StorageSessionConflictException) {
                throw new CompletionException(cause);
            }
            logger.log(Level.WARNING,
                    "Retrying Bloodstone storage from its last committed checkpoint", cause);
            return active.lastCommittedSession;
        });
    }

    private CompletableFuture<StorageWriteOutcome> closeStorageWithRetry(
            StorageSession session,
            byte[] payload,
            int attempt
    ) {
        return storage.closeStorage(session, payload, Instant.now())
                .exceptionallyCompose(exception -> {
                    if (!shouldRetryStorageClose(retryingFailedSaves, attempt)) {
                        return CompletableFuture.failedFuture(exception);
                    }
                    logger.log(
                            Level.WARNING,
                            "Retrying Bloodstone storage close after attempt " + attempt,
                            exception
                    );
                    return CompletableFuture.supplyAsync(
                            () -> null,
                            CompletableFuture.delayedExecutor(
                                    STORAGE_RETRY_DELAY_SECONDS,
                                    TimeUnit.SECONDS
                            )
                    ).thenCompose(ignored ->
                            closeStorageWithRetry(session, payload, attempt + 1));
                });
    }

    static boolean shouldRetryStorageClose(
            boolean retryingFailedSaves,
            int completedAttempts
    ) {
        return retryingFailedSaves
                && completedAttempts
                < STORAGE_CLOSE_MAXIMUM_ATTEMPTS;
    }

    private CompletableFuture<StorageWriteOutcome> checkpointStorageWithRetry(
            StorageSession session,
            byte[] payload,
            int attempt
    ) {
        return storage.checkpointStorage(
                        session,
                        payload,
                        Instant.now(),
                        STORAGE_LEASE_DURATION
                )
                .exceptionallyCompose(exception -> {
                    if (attempt >= STORAGE_CLOSE_MAXIMUM_ATTEMPTS) {
                        return CompletableFuture.failedFuture(exception);
                    }
                    return checkpointStorageWithRetry(session, payload, attempt + 1);
                });
    }

    private CompletableFuture<StorageSession> mapWriteOutcome(
            UUID playerId,
            ActiveStorage active,
            StorageWriteOutcome outcome
    ) {
        if (outcome instanceof StorageWriteOutcome.Saved saved) {
            return CompletableFuture.completedFuture(saved.session());
        }
        mainThreadExecutor.execute(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && player.getOpenInventory().getTopInventory() == active.inventory) {
                player.closeInventory();
                reject(player, BloodstoneServerConstants.STORAGE_STALE_SAVE);
            }
        });
        return CompletableFuture.failedFuture(
                new StorageSessionConflictException(
                        "Bloodstone storage session conflict for " + playerId));
    }

    private void purchaseExtraStorage(Player player) {
        if (!acceptingOperations) {
            reject(player, BloodstoneServerConstants.STORAGE_SHUTTING_DOWN);
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!pendingExtraStoragePurchases.add(playerId)) {
            reject(player, BloodstoneServerConstants.EXTRA_STORAGE_PURCHASE_PENDING);
            return;
        }
        if (itemService.countBloodAlloy(player.getInventory())
                < EXTRA_STORAGE_PRICE) {
            pendingExtraStoragePurchases.remove(playerId);
            messageService.sendRequiredCurrency(
                    player,
                    EXTRA_STORAGE_PRICE,
                    BloodstoneMessageService.Currency.BLOOD_ALLOY
            );
            return;
        }
        if (!itemService.removeBloodAlloy(
                player.getInventory(),
                EXTRA_STORAGE_PRICE
        )) {
            pendingExtraStoragePurchases.remove(playerId);
            messageService.sendRequiredCurrency(
                    player,
                    EXTRA_STORAGE_PRICE,
                    BloodstoneMessageService.Currency.BLOOD_ALLOY
            );
            return;
        }
        player.closeInventory();
        UUID operationId = UUID.randomUUID();
        unlockExtraStorageWithRetry(operationId, playerId, Instant.now())
                .thenAcceptAsync(outcome -> {
                    pendingExtraStoragePurchases.remove(playerId);
                    if (outcome instanceof ExtraStorageUnlockOutcome.PlayerNotFound) {
                        refundExtraStorageCost(player);
                        if (player.isOnline()) {
                            reject(
                                    player,
                                    BloodstoneServerConstants.EXTRA_STORAGE_PROFILE_NOT_READY
                            );
                        }
                        return;
                    }
                    if (outcome instanceof ExtraStorageUnlockOutcome.AlreadyUnlocked) {
                        refundExtraStorageCost(player);
                        locallyUnlockedExtraStorage.add(playerId);
                        playerService.refreshProfiles(Set.of(playerId));
                        if (player.isOnline()) {
                            openStorageMenu(player);
                        }
                        return;
                    }
                    locallyUnlockedExtraStorage.add(playerId);
                    playerService.refreshProfiles(Set.of(playerId));
                    if (!player.isOnline()) {
                        return;
                    }
                    BloodstoneText.sendActionBar(
                            player,
                            BloodstoneServerConstants.EXTRA_STORAGE_COST_ACTION_BAR_FORMAT,
                            Placeholder.unparsed(
                                    "price",
                                    Integer.toString(EXTRA_STORAGE_PRICE)
                            )
                    );
                    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0F, 2.0F);
                    BloodstoneText.sendMessage(
                            player,
                            BloodstoneServerConstants.EXTRA_STORAGE_PURCHASED
                    );
                    openStorageMenu(player);
                }, mainThreadExecutor)
                .exceptionally(exception -> {
                    mainThreadExecutor.execute(() -> {
                        pendingExtraStoragePurchases.remove(playerId);
                        if (player.isOnline()) {
                            refundExtraStorageCost(player);
                            reject(player, BloodstoneServerConstants.EXTRA_STORAGE_RECOVERING);
                        }
                    });
                    logger.log(Level.SEVERE, "Failed to unlock Extra Storage for " + playerId,
                            exception);
                    return null;
                });
    }

    private CompletableFuture<ExtraStorageUnlockOutcome> unlockExtraStorageWithRetry(
            UUID operationId,
            UUID playerId,
            Instant startedAt
    ) {
        return storage.unlockExtraStorage(operationId, playerId, startedAt)
                .exceptionallyCompose(exception -> {
                    if (!acceptingOperations) {
                        return CompletableFuture.failedFuture(exception);
                    }
                    logger.log(Level.WARNING,
                            "Retrying Extra Storage purchase " + operationId,
                            exception);
                    return CompletableFuture.supplyAsync(
                            () -> null,
                            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
                    ).thenCompose(ignored ->
                            unlockExtraStorageWithRetry(operationId, playerId, startedAt));
                });
    }

    private void refundExtraStorageCost(Player player) {
        if (player.isOnline()) {
            int leftovers = itemService.addBloodAlloy(
                    player.getInventory(),
                    EXTRA_STORAGE_PRICE
            );
            if (leftovers > 0) {
                player.getWorld().dropItemNaturally(
                        player.getLocation(),
                        itemService.createBloodAlloy(leftovers)
                );
            }
        }
    }

    private boolean isExtraUnlocked(UUID playerId) {
        return locallyUnlockedExtraStorage.contains(playerId)
                || playerService.profile(playerId).map(PlayerProfile::extraStorageUnlocked).orElse(false);
    }

    private boolean isEligible(Player player, StorageType type) {
        BloodstoneRank rank = BloodstoneRank.resolve(player);
        return BloodstoneStorageAccess.isEligible(
                rank,
                type,
                isExtraUnlocked(player.getUniqueId())
        );
    }

    private ItemStack storageButton(StorageType type, boolean unlocked) {
        if (type == StorageType.EXTRA) {
            return unlocked
                    ? BloodstoneServerConstants.EXTRA_STORAGE_UNLOCKED_ITEM.create()
                    : BloodstoneServerConstants.EXTRA_STORAGE_LOCKED_ITEM.create(
                            Placeholder.unparsed(
                                    "price",
                                    Integer.toString(EXTRA_STORAGE_PRICE)
                            )
                    );
        }
        TagResolver storageName = Placeholder.unparsed(
                "storage",
                type.displayName().toUpperCase(java.util.Locale.ROOT)
        );
        return unlocked
                ? BloodstoneServerConstants.STORAGE_UNLOCKED_ITEM.create(storageName)
                : BloodstoneServerConstants.STORAGE_LOCKED_ITEM.create(storageName);
    }

    private Component storageTitle(StorageType type) {
        return BloodstoneText.deserialize(
                BloodstoneServerConstants.STORAGE_INVENTORY_TITLE_FORMAT,
                Placeholder.unparsed("storage", type.displayName())
        );
    }

    private byte[] snapshot(Inventory inventory) {
        try {
            return BukkitItemSerialization.serializeInventory(inventory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize Bloodstone storage", exception);
        }
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }

    private static final class ActiveStorage implements InventoryHolder {
        private final Inventory inventory;
        private StorageSession lastCommittedSession;
        private CompletableFuture<StorageSession> tail;

        private ActiveStorage(
                StorageSession session,
                int inventorySize,
                Component inventoryTitle
        ) {
            this.lastCommittedSession = session;
            this.tail = CompletableFuture.completedFuture(session);
            this.inventory = Bukkit.createInventory(this, inventorySize, inventoryTitle);
        }

        @Override
        public @NonNull Inventory getInventory() {
            return inventory;
        }

        private StorageSession remember(StorageSession session) {
            lastCommittedSession = session;
            return session;
        }
    }

    private static final class StorageSessionConflictException extends IllegalStateException {
        private StorageSessionConflictException(String message) {
            super(message);
        }
    }
}
