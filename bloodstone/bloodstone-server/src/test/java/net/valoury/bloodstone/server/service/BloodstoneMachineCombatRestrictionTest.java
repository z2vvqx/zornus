package net.valoury.bloodstone.server.service;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneMachineCombatRestrictionTest {

    @Test
    void restrictsEveryRequestedMachineInteraction() {
        assertTrue(restricted(Material.PISTON_EXTENSION, Action.RIGHT_CLICK_BLOCK));
        assertTrue(restricted(Material.ANVIL, Action.RIGHT_CLICK_BLOCK));
        assertTrue(restricted(Material.FURNACE, Action.RIGHT_CLICK_BLOCK));
        assertTrue(restricted(Material.ENDER_CHEST, Action.RIGHT_CLICK_BLOCK));
        assertTrue(restricted(Material.ENDER_PORTAL_FRAME, Action.RIGHT_CLICK_BLOCK));
        assertTrue(restricted(Material.ENDER_PORTAL_FRAME, Action.LEFT_CLICK_BLOCK));
        assertTrue(restricted(Material.REDSTONE_BLOCK, Action.RIGHT_CLICK_BLOCK));
        assertTrue(restricted(Material.REDSTONE_BLOCK, Action.LEFT_CLICK_BLOCK));
    }

    @Test
    void leavesUnrelatedInteractionsAvailable() {
        assertFalse(restricted(Material.ANVIL, Action.LEFT_CLICK_BLOCK));
        assertFalse(restricted(Material.FURNACE, Action.PHYSICAL));
    }

    @Test
    void recognizesOnlyRequestedItemFrameRewards() {
        assertTrue(eligibleItemFrameReward(Material.DIAMOND_SWORD, (short) 0));
        assertTrue(eligibleItemFrameReward(Material.DIAMOND_AXE, (short) 0));
        assertTrue(eligibleItemFrameReward(Material.DIAMOND_HELMET, (short) 0));
        assertTrue(eligibleItemFrameReward(Material.DIAMOND_CHESTPLATE, (short) 0));
        assertTrue(eligibleItemFrameReward(Material.DIAMOND_LEGGINGS, (short) 0));
        assertTrue(eligibleItemFrameReward(Material.DIAMOND_BOOTS, (short) 0));
        assertTrue(eligibleItemFrameReward(Material.BOW, (short) 0));
        assertTrue(eligibleItemFrameReward(Material.ARROW, (short) 0));
        assertTrue(eligibleItemFrameReward(Material.GOLDEN_APPLE, (short) 0));

        assertFalse(eligibleItemFrameReward(Material.GOLDEN_APPLE, (short) 1));
        assertFalse(eligibleItemFrameReward(Material.IRON_SWORD, (short) 0));
        assertFalse(eligibleItemFrameReward(Material.GOLDEN_CARROT, (short) 0));
    }

    private static boolean restricted(Material material, Action action) {
        return BloodstoneMachineService.isCombatRestrictedMachineInteraction(
                material,
                action
        );
    }

    private static boolean eligibleItemFrameReward(Material material, short durability) {
        return BloodstoneMachineService.isEligibleItemFrameReward(material, durability);
    }
}
