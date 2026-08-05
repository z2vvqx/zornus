package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.model.BloodstoneItemClassification;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;

public final class BloodstoneWorldItemService {

    private static final short NORMAL_GOLDEN_APPLE_DATA = 0;
    private static final Set<Material> ITEM_FRAME_REWARD_MATERIALS = Set.of(
            Material.DIAMOND_SWORD,
            Material.DIAMOND_AXE,
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
            Material.BOW,
            Material.ARROW
    );

    private final Plugin plugin;
    private final BloodstoneItemService itemService;
    private final BloodstoneCurrencyService currencyService;
    private final BloodstoneCombatService combatService;
    private final BloodstonePresentationService presentationService;
    private final BloodstoneMessageService messageService;
    private final BloodDropPickupProtectionService
            bloodDropPickupProtectionService;

    public BloodstoneWorldItemService(
            Plugin plugin,
            BloodstoneItemService itemService,
            BloodstoneCurrencyService currencyService,
            BloodstoneCombatService combatService,
            BloodstonePresentationService presentationService,
            BloodstoneMessageService messageService,
            BloodDropPickupProtectionService bloodDropPickupProtectionService
    ) {
        this.plugin = plugin;
        this.itemService = itemService;
        this.currencyService = currencyService;
        this.combatService = combatService;
        this.presentationService = presentationService;
        this.messageService = messageService;
        this.bloodDropPickupProtectionService =
                bloodDropPickupProtectionService;
    }

    public void handleItemFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame itemFrame)
                || !isBloodstone(event.getPlayer())
                || event.getPlayer().getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (combatService.isTagged(player.getUniqueId())) {
            reject(player, BloodstoneServerConstants.ERROR_IN_BATTLE);
            return;
        }
        ItemStack displayed = itemFrame.getItem();
        if (displayed == null
                || !isEligibleItemFrameReward(
                displayed.getType(),
                displayed.getDurability()
        )) {
            return;
        }
        ItemStack reward = itemService.classify(
                displayed,
                BloodstoneItemClassification.INCLUSIVE
        );
        reward.setAmount(reward.getMaxStackSize());
        if (!canFit(player, reward)) {
            reject(player, BloodstoneServerConstants.ERROR_INVENTORY_SPACE);
            return;
        }
        player.getInventory().addItem(reward);
        player.playSound(
                player.getLocation(),
                Sound.ITEM_PICKUP,
                1.0F,
                presentationService.randomPitch(0.9F, 1.1F)
        );
    }

    public void handleResistancePotion(PlayerItemConsumeEvent event) {
        if (!isBloodstone(event.getPlayer())
                || !itemService.isResistancePotion(event.getItem())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () ->
                event.getPlayer().addPotionEffect(
                        itemService.createResistanceEffect(),
                        true
                ));
    }

    public void handleBloodPickup(PlayerPickupItemEvent event) {
        if (!isBloodstone(event.getPlayer())) {
            return;
        }
        if (!currencyService.isBlood(event.getItem().getItemStack())) {
            return;
        }
        if (bloodDropPickupProtectionService.isPickupPrevented(
                event.getItem(),
                event.getPlayer().getUniqueId()
        )) {
            event.setCancelled(true);
            return;
        }
        event.getPlayer().playSound(
                event.getPlayer().getLocation(),
                Sound.ITEM_PICKUP,
                0.35F,
                1.4F
        );
    }

    public void handleBloodPickupCompletion(PlayerPickupItemEvent event) {
        if (event.getRemaining() == 0) {
            bloodDropPickupProtectionService.clearPickupRestriction(
                    event.getItem()
            );
        }
    }

    public void handleBloodMerge(ItemMergeEvent event) {
        if (!currencyService.isBlood(event.getEntity().getItemStack())
                || !currencyService.isBlood(
                event.getTarget().getItemStack()
        )) {
            return;
        }
        if (bloodDropPickupProtectionService
                .hasConflictingPickupRestriction(
                        event.getEntity(),
                        event.getTarget()
                )) {
            event.setCancelled(true);
        }
    }

    public void handleBloodMergeCompletion(ItemMergeEvent event) {
        bloodDropPickupProtectionService.clearPickupRestriction(
                event.getEntity()
        );
    }

    public void handleItemDespawn(ItemDespawnEvent event) {
        bloodDropPickupProtectionService.clearPickupRestriction(
                event.getEntity()
        );
    }

    public void handlePossibleBloodDropRemoval(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)
                || !bloodDropPickupProtectionService
                .hasPickupRestriction(item)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (item.isDead() || !item.isValid()) {
                bloodDropPickupProtectionService.clearPickupRestriction(item);
            }
        });
    }

    public void handleDisposableItemSpawn(ItemSpawnEvent event) {
        if (!"bloodstone".equals(event.getLocation().getWorld().getName())) {
            return;
        }
        Material material = event.getEntity().getItemStack().getType();
        if (material == Material.GOLDEN_APPLE
                || material == Material.ARROW) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!event.getEntity().isDead()) {
                    event.getEntity().remove();
                }
            }, 160L);
        }
    }

    static boolean isEligibleItemFrameReward(
            Material material,
            short durability
    ) {
        return ITEM_FRAME_REWARD_MATERIALS.contains(material)
                || material == Material.GOLDEN_APPLE
                && durability == NORMAL_GOLDEN_APPLE_DATA;
    }

    private static boolean isBloodstone(Player player) {
        return "bloodstone".equals(player.getWorld().getName());
    }

    private static boolean canFit(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() >= 0) {
            return true;
        }
        for (ItemStack existing : player.getInventory().getContents()) {
            if (existing != null
                    && existing.isSimilar(item)
                    && existing.getAmount() + item.getAmount()
                    <= existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }
}
