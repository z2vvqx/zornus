package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneText;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class BloodstoneItemMenuLoreTest {

    @Test
    void shopLoreDescribesHiddenEnchantmentsAndPotionEffects() {
        assertEquals(
                List.of(
                        "<gray>Sharpness IV</gray>",
                        "<gray>Knockback II</gray>",
                        "<gray>Fire Aspect II</gray>"
                ),
                BloodstoneItemService.ShopProduct.SHARPNESS_IV_SWORD
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of(
                        "<gray>Power V</gray>",
                        "<gray>Punch II</gray>",
                        "<gray>Flame I</gray>",
                        "<gray>Infinity I</gray>",
                        "<gray>Unbreaking III</gray>"
                ),
                BloodstoneItemService.ShopProduct.POWER_V_BOW
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of(
                        "<gray>Protection IV</gray>",
                        "<gray>Unbreaking III</gray>"
                ),
                BloodstoneItemService.ShopProduct.PROTECTION_IV_CHESTPLATE
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of("<gray>Strength I (03:00)</gray>"),
                BloodstoneItemService.ShopProduct.STRENGTH_POTION
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of("<gray>Resistance (03:00)</gray>"),
                BloodstoneItemService.ShopProduct.RESISTANCE_POTION
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of("<gray>Speed I (03:00)</gray>"),
                BloodstoneItemService.ShopProduct.SPEED_POTION
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of("<gray>Fire Resistance I (03:00)</gray>"),
                BloodstoneItemService.ShopProduct.FIRE_RESISTANCE_POTION
                        .menuLoreTemplates()
        );
    }

    @Test
    void menuLorePrecedesExistingDetailsWithoutDuplicates() {
        assertEquals(
                List.of(
                        "\u00A77Unbreaking III",
                        "\u00A77Strength II (00:08)",
                        "\u00A77Soulbound"
                ),
                BloodstoneItemService.mergeMenuLore(
                        List.of("<gray>Unbreaking III</gray>"),
                        BloodstoneText.legacyLines(List.of(
                                "<gray>Strength II (00:08)</gray>",
                                "<gray>Soulbound</gray>"
                        ))
                )
        );
        assertEquals(
                List.of("\u00A77Resistance (03:00)"),
                BloodstoneItemService.mergeMenuLore(
                        List.of("<gray>Resistance (03:00)</gray>"),
                        List.of("\u00A77Resistance (03:00)")
                )
        );
    }

    @Test
    void standardShopPotionsAreLevelOneAndThreeMinuteVariants() {
        BloodstoneItemService itemService = new BloodstoneItemService();

        assertStandardPotion(
                PotionType.STRENGTH,
                itemService.createShopItem(
                        BloodstoneItemService.ShopProduct.STRENGTH_POTION
                )
        );
        assertStandardPotion(
                PotionType.SPEED,
                itemService.createShopItem(
                        BloodstoneItemService.ShopProduct.SPEED_POTION
                )
        );
        assertStandardPotion(
                PotionType.FIRE_RESISTANCE,
                itemService.createShopItem(
                        BloodstoneItemService.ShopProduct.FIRE_RESISTANCE_POTION
                )
        );
    }

    private void assertStandardPotion(
            PotionType expectedType,
            org.bukkit.inventory.ItemStack item
    ) {
        Potion expectedPotion = new Potion(expectedType);
        assertEquals(1, expectedPotion.getLevel());
        assertFalse(expectedPotion.hasExtendedDuration());
        assertEquals(expectedPotion.toDamageValue(), item.getDurability());
    }
}
