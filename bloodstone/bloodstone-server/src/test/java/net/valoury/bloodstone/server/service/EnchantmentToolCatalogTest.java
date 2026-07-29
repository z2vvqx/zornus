package net.valoury.bloodstone.server.service;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnchantmentToolCatalogTest {

    @Test
    void machineCatalogShowsTheCompleteOptionsForEachSupportedItem() {
        assertOptions(
                Material.DIAMOND_SWORD,
                "Sharpness IV",
                "Fire Aspect II",
                "Knockback II",
                "Unbreaking III"
        );
        assertOptions(
                Material.DIAMOND_AXE,
                "Sharpness IV",
                "Fire Aspect II",
                "Knockback II",
                "Unbreaking III"
        );
        assertOptions(
                Material.BOW,
                "Power V",
                "Punch II",
                "Flame I",
                "Unbreaking III"
        );
        assertOptions(
                Material.DIAMOND_HELMET,
                "Protection IV",
                "Unbreaking III",
                "Aqua Affinity I",
                "Respiration III"
        );
        assertOptions(
                Material.DIAMOND_CHESTPLATE,
                "Protection IV",
                "Unbreaking III",
                "Thorns III"
        );
        assertOptions(
                Material.DIAMOND_LEGGINGS,
                "Protection IV",
                "Unbreaking III"
        );
        assertOptions(
                Material.DIAMOND_BOOTS,
                "Protection IV",
                "Unbreaking III",
                "Depth Strider III",
                "Feather Falling IV"
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

    @Test
    void selectionsAreCheckedAgainstTheCurrentHeldItemEnchantments() {
        assertFalse(EnchantmentToolAction.ENCHANT.isSelectionAvailable(4, 4));
        assertTrue(EnchantmentToolAction.ENCHANT.isSelectionAvailable(0, 4));
        assertTrue(EnchantmentToolAction.DISENCHANT.isSelectionAvailable(3, 3));
        assertFalse(EnchantmentToolAction.DISENCHANT.isSelectionAvailable(0, 3));
    }

    private static void assertOptions(
            Material material,
            String... expectedOptions
    ) {
        List<String> actualOptions = EnchantmentToolCatalog.optionsFor(material)
                .stream()
                .map(option -> option.displayName() + " " + roman(option.level()))
                .toList();
        assertEquals(List.of(expectedOptions), actualOptions);
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }
}
