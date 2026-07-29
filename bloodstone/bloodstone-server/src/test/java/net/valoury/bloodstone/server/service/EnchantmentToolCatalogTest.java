package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class EnchantmentToolCatalogTest {

    @Test
    void optionInventoryGrowsWithinTheBukkitChestLimit() {
        assertEquals(27, EnchantmentToolCatalog.inventorySizeFor(1));
        assertEquals(27, EnchantmentToolCatalog.inventorySizeFor(9));
        assertEquals(36, EnchantmentToolCatalog.inventorySizeFor(10));
        assertEquals(45, EnchantmentToolCatalog.inventorySizeFor(19));
        assertEquals(54, EnchantmentToolCatalog.inventorySizeFor(28));
        assertEquals(54, EnchantmentToolCatalog.inventorySizeFor(45));
        assertThrows(
                IllegalArgumentException.class,
                () -> EnchantmentToolCatalog.inventorySizeFor(0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> EnchantmentToolCatalog.inventorySizeFor(46)
        );
    }

    @Test
    void disenchanterCooldownOffersAreIndependentFromEnchanterOffers() {
        assertEquals(
                "bow::power",
                EnchantmentToolAction.ENCHANT.offerKey("bow::power")
        );
        assertEquals(
                "disenchant::bow::power",
                EnchantmentToolAction.DISENCHANT.offerKey("bow::power")
        );
    }
}
