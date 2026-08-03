package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

import java.util.UUID;

public final class BloodstoneMachineService {

    private final BloodstoneCombatService combatService;
    private final BloodstoneMenuService menuService;
    private final BloodstoneStorageService storageService;
    private final BloodstoneEnchanterService enchanterService;
    private final BloodstoneAxeFuserService axeFuserService;
    private final BloodstonePlayerService playerService;
    private final BloodstoneRandomBoxService randomBoxService;
    private final BloodstoneRepairService repairService;
    private final BloodstoneWorldItemService worldItemService;
    private final BloodstoneUtilityStationService utilityStationService;
    private final BloodstoneMessageService messageService;

    private volatile boolean acceptingOperations = true;

    public BloodstoneMachineService(
            BloodstoneCombatService combatService,
            BloodstoneMenuService menuService,
            BloodstoneStorageService storageService,
            BloodstoneEnchanterService enchanterService,
            BloodstoneAxeFuserService axeFuserService,
            BloodstonePlayerService playerService,
            BloodstoneRandomBoxService randomBoxService,
            BloodstoneRepairService repairService,
            BloodstoneWorldItemService worldItemService,
            BloodstoneUtilityStationService utilityStationService,
            BloodstoneMessageService messageService
    ) {
        this.combatService = combatService;
        this.menuService = menuService;
        this.storageService = storageService;
        this.enchanterService = enchanterService;
        this.axeFuserService = axeFuserService;
        this.playerService = playerService;
        this.randomBoxService = randomBoxService;
        this.repairService = repairService;
        this.worldItemService = worldItemService;
        this.utilityStationService = utilityStationService;
        this.messageService = messageService;
    }

    public boolean isUnavailable(UUID playerId) {
        return !acceptingOperations || !playerService.isLoaded(playerId);
    }

    public void handleBlockInteraction(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null
                || !isBloodstone(player)
                || player.getGameMode() == GameMode.ADVENTURE) {
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
        if (isCombatRestrictedMachineInteraction(
                material,
                event.getAction()
        ) && combatService.isTagged(player.getUniqueId())) {
            event.setCancelled(true);
            reject(player, BloodstoneServerConstants.ERROR_IN_BATTLE);
            return;
        }
        if (material == Material.ENDER_CHEST
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
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
        } else if (material == Material.ANVIL
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            repairService.begin(player, block);
        } else if (material == Material.FURNACE
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            axeFuserService.open(player, block);
        } else if (isPistonHead(material)
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            randomBoxService.begin(player, block);
        } else if (material == Material.REDSTONE_BLOCK) {
            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                menuService.exchangeBloodForAlloy(player);
            } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                menuService.exchangeAlloyForBlood(player);
            }
        } else if ((material == Material.SIGN_POST
                || material == Material.WALL_SIGN)
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && block.getState() instanceof Sign sign) {
            event.setCancelled(true);
            utilityStationService.handle(player, sign);
        }
    }

    public void handleItemFrame(PlayerInteractEntityEvent event) {
        worldItemService.handleItemFrame(event);
    }

    public void handleResistancePotion(PlayerItemConsumeEvent event) {
        worldItemService.handleResistancePotion(event);
    }

    public void handleBloodPickup(PlayerPickupItemEvent event) {
        worldItemService.handleBloodPickup(event);
    }

    public void handleDisposableItemSpawn(ItemSpawnEvent event) {
        worldItemService.handleDisposableItemSpawn(event);
    }

    public void shutdown() {
        acceptingOperations = false;
        repairService.shutdown();
        randomBoxService.shutdown();
    }

    static boolean isCombatRestrictedMachineInteraction(
            Material material,
            Action action
    ) {
        if (action == Action.RIGHT_CLICK_BLOCK) {
            return material == Material.ENDER_CHEST
                    || material == Material.ENDER_PORTAL_FRAME
                    || material == Material.ANVIL
                    || material == Material.FURNACE
                    || material == Material.REDSTONE_BLOCK
                    || isPistonHead(material);
        }
        return action == Action.LEFT_CLICK_BLOCK
                && (material == Material.ENDER_PORTAL_FRAME
                || material == Material.REDSTONE_BLOCK);
    }

    private static boolean isPistonHead(Material material) {
        return material == Material.PISTON_EXTENSION
                || material.name().equals("PISTON_HEAD");
    }

    private void reject(Player player, String message) {
        messageService.sendError(player, message);
    }

    private static boolean isBloodstone(Player player) {
        return "bloodstone".equals(player.getWorld().getName());
    }
}
