package net.valoury.bloodstone.server.service;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneWorldItemServiceTest {

    @Test
    void recognizesOnlyConfiguredItemFrameRewards() {
        assertTrue(eligible(Material.DIAMOND_SWORD, (short) 0));
        assertTrue(eligible(Material.DIAMOND_AXE, (short) 0));
        assertTrue(eligible(Material.DIAMOND_HELMET, (short) 0));
        assertTrue(eligible(Material.DIAMOND_CHESTPLATE, (short) 0));
        assertTrue(eligible(Material.DIAMOND_LEGGINGS, (short) 0));
        assertTrue(eligible(Material.DIAMOND_BOOTS, (short) 0));
        assertTrue(eligible(Material.BOW, (short) 0));
        assertTrue(eligible(Material.ARROW, (short) 0));
        assertTrue(eligible(Material.GOLDEN_APPLE, (short) 0));

        assertFalse(eligible(Material.GOLDEN_APPLE, (short) 1));
        assertFalse(eligible(Material.IRON_SWORD, (short) 0));
        assertFalse(eligible(Material.GOLDEN_CARROT, (short) 0));
    }

    private static boolean eligible(Material material, short durability) {
        return BloodstoneWorldItemService.isEligibleItemFrameReward(
                material,
                durability
        );
    }
}
