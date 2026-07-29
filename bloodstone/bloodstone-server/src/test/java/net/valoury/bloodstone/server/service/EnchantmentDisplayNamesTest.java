package net.valoury.bloodstone.server.service;

import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class EnchantmentDisplayNamesTest {

    @Test
    void everyCarbonEnchantmentUsesItsPlayerFacingName() {
        Map<Enchantment, String> expectedNames = Map.ofEntries(
                entry(Enchantment.PROTECTION_ENVIRONMENTAL, "Protection"),
                entry(Enchantment.PROTECTION_FIRE, "Fire Protection"),
                entry(Enchantment.PROTECTION_FALL, "Feather Falling"),
                entry(Enchantment.PROTECTION_EXPLOSIONS, "Blast Protection"),
                entry(Enchantment.PROTECTION_PROJECTILE, "Projectile Protection"),
                entry(Enchantment.OXYGEN, "Respiration"),
                entry(Enchantment.WATER_WORKER, "Aqua Affinity"),
                entry(Enchantment.THORNS, "Thorns"),
                entry(Enchantment.DEPTH_STRIDER, "Depth Strider"),
                entry(Enchantment.DAMAGE_ALL, "Sharpness"),
                entry(Enchantment.DAMAGE_UNDEAD, "Smite"),
                entry(Enchantment.DAMAGE_ARTHROPODS, "Bane of Arthropods"),
                entry(Enchantment.KNOCKBACK, "Knockback"),
                entry(Enchantment.FIRE_ASPECT, "Fire Aspect"),
                entry(Enchantment.LOOT_BONUS_MOBS, "Looting"),
                entry(Enchantment.DIG_SPEED, "Efficiency"),
                entry(Enchantment.SILK_TOUCH, "Silk Touch"),
                entry(Enchantment.DURABILITY, "Unbreaking"),
                entry(Enchantment.LOOT_BONUS_BLOCKS, "Fortune"),
                entry(Enchantment.ARROW_DAMAGE, "Power"),
                entry(Enchantment.ARROW_KNOCKBACK, "Punch"),
                entry(Enchantment.ARROW_FIRE, "Flame"),
                entry(Enchantment.ARROW_INFINITE, "Infinity"),
                entry(Enchantment.LUCK, "Luck of the Sea"),
                entry(Enchantment.LURE, "Lure")
        );

        assertEquals(25, expectedNames.size());
        for (Map.Entry<Enchantment, String> entry : expectedNames.entrySet()) {
            assertEquals(
                    entry.getValue(),
                    EnchantmentDisplayNames.displayName(entry.getKey())
            );
        }
    }
}
