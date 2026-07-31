package net.valoury.bloodstone.server.listener.inventory;

import net.valoury.bloodstone.server.service.BloodstoneAxeFuserService;
import net.valoury.bloodstone.server.service.BloodstoneEnchanterService;
import net.valoury.bloodstone.server.service.BloodstoneItemService;
import net.valoury.bloodstone.server.service.BloodstoneMenuService;
import net.valoury.bloodstone.server.service.BloodstoneStorageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.jspecify.annotations.NonNull;

public final class BloodstoneInventoryListener implements Listener {

    private final BloodstoneMenuService menuService;
    private final BloodstoneStorageService storageService;
    private final BloodstoneEnchanterService enchanterService;
    private final BloodstoneAxeFuserService axeFuserService;
    private final BloodstoneItemService itemService;

    public BloodstoneInventoryListener(
            BloodstoneMenuService menuService,
            BloodstoneStorageService storageService,
            BloodstoneEnchanterService enchanterService,
            BloodstoneAxeFuserService axeFuserService,
            BloodstoneItemService itemService
    ) {
        this.menuService = menuService;
        this.storageService = storageService;
        this.enchanterService = enchanterService;
        this.axeFuserService = axeFuserService;
        this.itemService = itemService;
    }

    @EventHandler
    public void onInventoryClick(@NonNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !menuService.isInBloodstone(player)) {
            return;
        }
        menuService.handleInventoryClick(event);
        storageService.handleInventoryClick(event);
        enchanterService.handleInventoryClick(event);
        axeFuserService.handleInventoryClick(event);
        enchanterService.handleInventoryClickForLapis(event);
    }

    @EventHandler
    public void onInventoryOpen(@NonNull InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !menuService.isInBloodstone(player)) {
            return;
        }
        enchanterService.handleInventoryOpen(event);
    }

    @EventHandler
    public void onInventoryDrag(@NonNull InventoryDragEvent event) {
        axeFuserService.handleInventoryDrag(event);
    }

    @EventHandler
    public void onInventoryClose(@NonNull InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            storageService.handleInventoryClose(player, event.getInventory());
            if (!menuService.isInBloodstone(player)) {
                return;
            }
            enchanterService.handleInventoryClose(player, event.getInventory());
            axeFuserService.handleInventoryClose(player, event.getView().title());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantItem(@NonNull EnchantItemEvent event) {
        if (!menuService.isInBloodstone(event.getEnchanter())) {
            return;
        }
        enchanterService.handleNormalEnchant(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(@NonNull PlayerDropItemEvent event) {
        if (!menuService.isInBloodstone(event.getPlayer())) {
            return;
        }
        if (itemService.isSoulbound(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        enchanterService.handleArtificialLapisDrop(event);
    }
}
