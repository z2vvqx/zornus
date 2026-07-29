package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.valoury.bloodstone.server.storage.BloodstoneStorage;

public final class BloodstoneService {

    private static final int RESPAWN_HORIZONTAL_RADIUS = 1;
    private static final int BASELINE_STACK_SIZE = 64;
    private static final int SWORD_SLOT = 0;
    private static final int FISHING_ROD_SLOT = 1;
    private static final int AXE_SLOT = 2;
    private static final int BOW_SLOT = 3;
    private static final int GOLDEN_APPLE_SLOT = 4;
    private static final int ARROW_SLOT = 17;
    private static final int DEATH_PARTICLE_COUNT = 20;
    private static final float DEATH_PARTICLE_OFFSET_X = 0.5F;
    private static final float DEATH_PARTICLE_OFFSET_Y = 1.0F;
    private static final float DEATH_PARTICLE_OFFSET_Z = 0.5F;
    private static final int PARTICLE_VISIBILITY_RADIUS = 64;
    private static final Sound BASELINE_SOUND = Sound.ITEM_PICKUP;

    private final BloodstoneItemService itemService;
    private final BloodstoneStorage storage;
    private final BloodstonePlayerService playerService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final Logger logger;
    private final Map<UUID, List<PendingSoulboundItem>> pendingSoulboundItems = new HashMap<>();
    private volatile boolean acceptingRecoveries = true;

    public BloodstoneService(
            BloodstoneItemService itemService,
            BloodstoneStorage storage,
            BloodstonePlayerService playerService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            Logger logger
    ) {
        this.itemService = itemService;
        this.storage = storage;
        this.playerService = playerService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.logger = logger;
    }

    public boolean isInBloodstoneWorld(Player player) {
        return BloodstoneServerConstants.WORLD_NAME.equals(player.getWorld().getName());
    }

    public void handlePlayerDeath(Player player, List<ItemStack> drops) {
        player.spigot().respawn();
        playDeathParticles(player.getLocation());
        Iterator<ItemStack> dropIterator = drops.iterator();
        while (dropIterator.hasNext()) {
            ItemStack item = dropIterator.next();
            if (itemService.isSoulbound(item)) {
                if (reserveSoulboundItem(player.getUniqueId(), item)) {
                    dropIterator.remove();
                }
                continue;
            }
            if (itemService.isInclusive(item) || itemService.isExclusive(item)) {
                dropIterator.remove();
            }
        }
    }

    public Location selectRespawnLocation(World world) {
        if (!BloodstoneServerConstants.WORLD_NAME.equals(world.getName())) {
            throw new IllegalArgumentException("Cannot select a Bloodstone spawn in world " + world.getName());
        }

        Location worldSpawn = world.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        int offsetX = ThreadLocalRandom.current().nextInt(
                -RESPAWN_HORIZONTAL_RADIUS,
                RESPAWN_HORIZONTAL_RADIUS + 1
        );
        int offsetZ = ThreadLocalRandom.current().nextInt(
                -RESPAWN_HORIZONTAL_RADIUS,
                RESPAWN_HORIZONTAL_RADIUS + 1
        );

        return new Location(
                world,
                worldSpawn.getBlockX() + offsetX,
                worldSpawn.getY(),
                worldSpawn.getBlockZ() + offsetZ
        );
    }

    public void restoreBaselineKit(Player player) {
        PlayerInventory inventory = player.getInventory();

        inventory.setItem(
                SWORD_SLOT,
                itemService.createInclusiveItem(Material.DIAMOND_SWORD, 1)
        );
        inventory.setItem(
                FISHING_ROD_SLOT,
                itemService.createInclusiveItem(Material.FISHING_ROD, 1)
        );
        inventory.setItem(
                AXE_SLOT,
                itemService.createInclusiveItem(Material.DIAMOND_AXE, 1)
        );
        inventory.setItem(
                BOW_SLOT,
                itemService.createInclusiveItem(Material.BOW, 1)
        );
        inventory.setItem(
                GOLDEN_APPLE_SLOT,
                itemService.createInclusiveItem(
                        Material.GOLDEN_APPLE,
                        BASELINE_STACK_SIZE
                )
        );
        inventory.setItem(
                ARROW_SLOT,
                itemService.createInclusiveItem(
                        Material.ARROW,
                        BASELINE_STACK_SIZE
                )
        );

        inventory.setHelmet(itemService.createInclusiveItem(Material.DIAMOND_HELMET, 1));
        inventory.setChestplate(itemService.createInclusiveItem(Material.DIAMOND_CHESTPLATE, 1));
        inventory.setLeggings(itemService.createInclusiveItem(Material.DIAMOND_LEGGINGS, 1));
        inventory.setBoots(itemService.createInclusiveItem(Material.DIAMOND_BOOTS, 1));
        returnPendingSoulboundItems(player);
    }

    public void playBaselineRestoredFeedback(Player player) {
        Location playerLocation = player.getLocation();
        player.playSound(
                playerLocation,
                BASELINE_SOUND,
                1.0F,
                0.9F
        );
        player.playSound(
                playerLocation,
                BASELINE_SOUND,
                1.0F,
                1.1F
        );
        BloodstoneText.sendActionBar(
                player,
                BloodstoneServerConstants.BASELINE_RESTORED_ACTION_BAR
        );
    }

    private void playDeathParticles(Location deathLocation) {
        deathLocation.getWorld().spigot().playEffect(
                deathLocation,
                Effect.EXPLOSION,
                0,
                0,
                DEATH_PARTICLE_OFFSET_X,
                DEATH_PARTICLE_OFFSET_Y,
                DEATH_PARTICLE_OFFSET_Z,
                0.0F,
                DEATH_PARTICLE_COUNT,
                PARTICLE_VISIBILITY_RADIUS
        );
    }

    private boolean reserveSoulboundItem(UUID playerId, ItemStack item) {
        if (!acceptingRecoveries) {
            return false;
        }
        List<PendingSoulboundItem> pending =
                pendingSoulboundItems.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        UUID operationId = UUID.randomUUID();
        try {
            byte[] payload = BukkitItemSerialization.serializeItem(item);
            CompletableFuture<net.valoury.bloodstone.server.model.SoulboundRecovery> reservation =
                    reserveSoulboundWithRetry(operationId, playerId, payload);
            pending.add(new PendingSoulboundItem(operationId, item.clone(), reservation));
            return true;
        } catch (IOException exception) {
            logger.log(Level.SEVERE,
                    "Failed to serialize Soulbound item for " + playerId
                            + "; leaving it in the public death drops",
                    exception);
            return false;
        }
    }

    private CompletableFuture<net.valoury.bloodstone.server.model.SoulboundRecovery>
    reserveSoulboundWithRetry(UUID operationId, UUID playerId, byte[] payload) {
        return storage.reserveSoulboundRecovery(operationId, playerId, payload, Instant.now())
                .exceptionallyCompose(exception -> {
                    logger.log(Level.SEVERE,
                            "Failed to reserve Soulbound recovery " + operationId
                                    + "; retrying while the server remains online",
                            exception);
                    return CompletableFuture.supplyAsync(
                            () -> null,
                            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
                    ).thenCompose(ignored ->
                            reserveSoulboundWithRetry(operationId, playerId, payload));
                });
    }

    private void returnPendingSoulboundItems(Player player) {
        List<PendingSoulboundItem> pending = pendingSoulboundItems.remove(player.getUniqueId());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (PendingSoulboundItem pendingItem : pending) {
            pendingItem.reservation().thenComposeAsync(recovery ->
                    playerService.deliverReservedItem(
                            player,
                            pendingItem.operationId(),
                            pendingItem.item(),
                            true,
                            () -> storage.completeSoulboundRecovery(
                                    recovery.operationId(),
                                    recovery.playerId()
                            )
                    )
            , mainThreadExecutor).exceptionally(exception -> {
                logger.log(Level.SEVERE,
                        "Failed to return Soulbound item " + pendingItem.operationId(),
                        exception);
                return null;
            });
        }
    }

    public CompletableFuture<Void> shutdown() {
        acceptingRecoveries = false;
        CompletableFuture<?>[] reservations = pendingSoulboundItems.values().stream()
                .flatMap(List::stream)
                .map(PendingSoulboundItem::reservation)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(reservations);
    }

    private record PendingSoulboundItem(
            UUID operationId,
            ItemStack item,
            CompletableFuture<net.valoury.bloodstone.server.model.SoulboundRecovery> reservation
    ) {
        private PendingSoulboundItem {
            item = item.clone();
        }

        @Override
        public ItemStack item() {
            return item.clone();
        }
    }
}
