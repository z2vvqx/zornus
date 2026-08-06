package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.CombinedEffectAxeDefinitions;
import net.valoury.bloodstone.server.EffectAxeDefinitions;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.model.BloodstoneShopProduct;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class BloodstoneItemMenuLoreTest {

    @Test
    void shopLoreDescribesHiddenEnchantmentsAndPotionEffects() {
        assertEquals(
                List.of(
                        "<gray>Sharpness IV</gray>",
                        "<gray>Knockback II</gray>",
                        "<gray>Fire Aspect II</gray>"
                ),
                BloodstoneShopProduct.SHARPNESS_IV_SWORD
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
                BloodstoneShopProduct.POWER_V_BOW
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of(
                        "<gray>Protection IV</gray>",
                        "<gray>Unbreaking III</gray>"
                ),
                BloodstoneShopProduct.PROTECTION_IV_CHESTPLATE
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of("<gray>Strength I (03:00)</gray>"),
                BloodstoneShopProduct.STRENGTH_POTION
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of("<gray>Resistance (03:00)</gray>"),
                BloodstoneShopProduct.RESISTANCE_POTION
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of("<gray>Speed I (03:00)</gray>"),
                BloodstoneShopProduct.SPEED_POTION
                        .menuLoreTemplates()
        );
        assertEquals(
                List.of("<gray>Fire Resistance I (03:00)</gray>"),
                BloodstoneShopProduct.FIRE_RESISTANCE_POTION
                        .menuLoreTemplates()
        );
    }

    @Test
    void menuLorePrecedesExistingDetailsWithoutDuplicates() {
        assertEquals(
                BloodstoneText.deserializeLines(List.of(
                        "<gray>Unbreaking III</gray>",
                        "<gray>Strength II (00:08)</gray>",
                        "<gray>Soulbound</gray>"
                )),
                BloodstoneItemDisplayService.mergeMenuLore(
                        List.of("<gray>Unbreaking III</gray>"),
                        BloodstoneText.deserializeLines(List.of(
                                "<gray>Strength II (00:08)</gray>",
                                "<gray>Soulbound</gray>"
                        ))
                )
        );
        assertEquals(
                BloodstoneText.deserializeLines(List.of(
                        "<gray>Resistance (03:00)</gray>"
                )),
                BloodstoneItemDisplayService.mergeMenuLore(
                        List.of("<gray>Resistance (03:00)</gray>"),
                        BloodstoneText.deserializeLines(List.of(
                                "<gray>Resistance (03:00)</gray>"
                        ))
                )
        );
    }

    @Test
    void standardShopPotionsAreLevelOneAndThreeMinuteVariants() {
        BloodstoneItemService itemService = new BloodstoneItemService();

        assertStandardPotion(
                PotionType.STRENGTH,
                itemService.createShopItem(
                        BloodstoneShopProduct.STRENGTH_POTION
                )
        );
        assertStandardPotion(
                PotionType.SPEED,
                itemService.createShopItem(
                        BloodstoneShopProduct.SPEED_POTION
                )
        );
        assertStandardPotion(
                PotionType.FIRE_RESISTANCE,
                itemService.createShopItem(
                        BloodstoneShopProduct.FIRE_RESISTANCE_POTION
                )
        );
    }

    @Test
    void effectAxesAreOrderedByDescendingRankPriceFromLeftToRight() {
        List<String> expectedOrder = List.of(
                "strength",
                "wither",
                "poison",
                "speed",
                "blindness",
                "weakness"
        );
        for (BloodstoneRank rank : BloodstoneRank.values()) {
            assertEquals(
                    expectedOrder,
                    BloodstoneMenuService.effectAxesByDescendingPrice(rank)
                            .stream()
                            .map(EffectAxeDefinitions.EffectAxeDefinition::id)
                            .toList()
            );
        }
    }

    @Test
    void axeFuserUsesEnchanterLoreStyling() {
        assertEquals(16, BloodstoneAxeFuserService.FUSION_BLOOD_ALLOY_COST);
        assertEquals(
                "<dark_purple>Fuse Axes</dark_purple>",
                BloodstoneServerConstants.AXE_FUSER_FUSE_ITEM.nameTemplate()
        );
        assertEquals(
                List.of(
                        "",
                        " <gray>Fuse the two selected effect axes.</gray>",
                        "",
                        " <gray>Price: <dark_red><bold><price>⛃</bold> blood alloy</dark_red></gray>",
                        "",
                        "<green>➟ Click to fuse these axes!</green>"
                ),
                BloodstoneServerConstants.AXE_FUSER_FUSE_ITEM.loreTemplates()
        );
        assertEquals(
                List.of(
                        "",
                        " <gray>Select this effect axe for fusion.</gray>",
                        "",
                        "<green>➟ Click to select this axe!</green>"
                ),
                BloodstoneServerConstants.AXE_FUSER_UNSELECTED_AXE_LORE
        );
        assertEquals(
                List.of(
                        "",
                        " <gray>This effect axe is selected for fusion.</gray>",
                        "",
                        "<red>➟ Click to deselect this axe!</red>"
                ),
                BloodstoneServerConstants.AXE_FUSER_SELECTED_AXE_LORE
        );
    }

    @Test
    void axeFuserAccessIsExclusiveToValorians() {
        assertTrue(BloodstoneAxeFuserService.hasAxeFuserAccess(
                BloodstoneRank.VALORIAN
        ));
        for (BloodstoneRank rank : BloodstoneRank.values()) {
            if (rank != BloodstoneRank.VALORIAN) {
                assertFalse(
                        BloodstoneAxeFuserService.hasAxeFuserAccess(rank),
                        rank + " unexpectedly has Axe Fuser access"
                );
            }
        }
    }

    @Test
    void effectAxeSharpnessReflectsWhetherTheAxeIsFused() {
        assertEquals(
                3,
                BloodstoneEffectAxeService.sharpnessLevel(
                        EffectAxeDefinitions.SPEED
                )
        );
        assertEquals(
                4,
                BloodstoneEffectAxeService.sharpnessLevel(
                        CombinedEffectAxeDefinitions.BERSERKER
                )
        );
    }

    @Test
    void axeFuserAddsAndCapsRemainingDurability() {
        assertEquals(
                900,
                BloodstoneAxeFuserService.mergedRemainingDurability(
                        1_000,
                        400,
                        500
                )
        );
        assertEquals(
                1_000,
                BloodstoneAxeFuserService.mergedRemainingDurability(
                        1_000,
                        600,
                        500
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BloodstoneAxeFuserService.mergedRemainingDurability(
                        1_000,
                        0,
                        500
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
