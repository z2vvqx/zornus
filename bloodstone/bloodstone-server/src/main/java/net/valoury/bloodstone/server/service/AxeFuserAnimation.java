package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class AxeFuserAnimation {

    private static final long DURATION_TICKS = 50L;

    private final Plugin plugin;
    private final BloodstoneStorage storage;
    private final BloodstonePlayerService playerService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstonePresentationService presentationService;
    private final Logger logger;
    private final Set<Item> displays = new HashSet<>();

    AxeFuserAnimation(
            Plugin plugin,
            BloodstoneStorage storage,
            BloodstonePlayerService playerService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstonePresentationService presentationService,
            Logger logger
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerService = playerService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.presentationService = presentationService;
        this.logger = logger;
    }

    void play(
            Player player,
            Location blockLocation,
            ItemStack firstOriginal,
            ItemStack secondOriginal,
            ItemStack fusedAxe,
            UUID operationId,
            Runnable finishOperation,
            Runnable recoverOperation
    ) {
        Location center = blockLocation.clone().add(0.5D, 1.25D, 0.5D);
        Item firstDisplay = spawnDisplay(
                center.clone().add(-0.4D, 0.0D, 0.0D),
                firstOriginal
        );
        Item secondDisplay = spawnDisplay(
                center.clone().add(0.4D, 0.0D, 0.0D),
                secondOriginal
        );
        for (int animationStep = 1; animationStep <= 6; animationStep++) {
            int scheduledStep = animationStep;
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    playStep(
                            center,
                            firstDisplay,
                            secondDisplay,
                            fusedAxe,
                            scheduledStep
                    ), animationStep * 8L);
        }
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> deliver(
                        player,
                        firstDisplay,
                        secondDisplay,
                        fusedAxe,
                        operationId,
                        finishOperation,
                        recoverOperation
                ),
                DURATION_TICKS
        );
    }

    private void playStep(
            Location center,
            Item firstDisplay,
            Item secondDisplay,
            ItemStack fusedAxe,
            int animationStep
    ) {
        double offset = 0.4D * (6 - animationStep) / 6.0D;
        double height = 0.05D * animationStep;
        if (!firstDisplay.isDead()) {
            firstDisplay.teleport(
                    center.clone().add(-offset, height, 0.0D)
            );
        }
        if (!secondDisplay.isDead()) {
            secondDisplay.teleport(
                    center.clone().add(offset, height, 0.0D)
            );
        }
        if (animationStep == 5) {
            if (!firstDisplay.isDead()) {
                firstDisplay.setItemStack(fusedAxe.clone());
            }
            removeDisplay(secondDisplay);
        }
        center.getWorld().spigot().playEffect(
                center.clone().add(0.0D, height, 0.0D),
                Effect.WITCH_MAGIC,
                0,
                0,
                0.25F,
                0.35F,
                0.25F,
                0.05F,
                12,
                48
        );
        float pitch = presentationService.randomPitch(0.6F, 0.8F);
        center.getWorld().playSound(
                center,
                Sound.ANVIL_LAND,
                0.5F,
                pitch
        );
        center.getWorld().playSound(
                center,
                Sound.ZOMBIE_METAL,
                0.5F,
                pitch
        );
        center.getWorld().playSound(
                center,
                Sound.DIG_STONE,
                0.5F,
                pitch
        );
    }

    private void deliver(
            Player player,
            Item firstDisplay,
            Item secondDisplay,
            ItemStack fusedAxe,
            UUID operationId,
            Runnable finishOperation,
            Runnable recoverOperation
    ) {
        removeDisplay(firstDisplay);
        removeDisplay(secondDisplay);
        if (!player.isOnline()) {
            finishOperation.run();
            return;
        }
        playerService.deliverReservedItem(
                        player,
                        operationId,
                        fusedAxe,
                        true,
                        () -> storage.completeAxeFuserOperation(
                                operationId,
                                player.getUniqueId()
                        )
                )
                .thenAcceptAsync(ignored -> finishOperation.run(),
                        mainThreadExecutor)
                .exceptionally(exception -> {
                    logger.log(
                            Level.SEVERE,
                            "Failed to complete Axe Fuser delivery",
                            exception
                    );
                    mainThreadExecutor.execute(() -> {
                        finishOperation.run();
                        if (player.isOnline()) {
                            recoverOperation.run();
                        }
                    });
                    return null;
                });
    }

    private Item spawnDisplay(Location location, ItemStack item) {
        Item display = location.getWorld().dropItem(location, item.clone());
        displays.add(display);
        display.setPickupDelay(Integer.MAX_VALUE);
        display.setVelocity(new Vector());
        return display;
    }

    private void removeDisplay(Item display) {
        displays.remove(display);
        display.remove();
    }

    void shutdown() {
        displays.forEach(Item::remove);
        displays.clear();
    }
}
