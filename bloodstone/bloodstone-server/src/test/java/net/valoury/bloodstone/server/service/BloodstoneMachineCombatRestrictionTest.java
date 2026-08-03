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

    private static boolean restricted(Material material, Action action) {
        return BloodstoneMachineService.isCombatRestrictedMachineInteraction(
                material,
                action
        );
    }
}
