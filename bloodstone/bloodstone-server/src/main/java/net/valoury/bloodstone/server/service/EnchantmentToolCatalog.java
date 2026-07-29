package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EnchantmentToolCatalog {

    private static final Set<Material> SUPPORTED_ITEMS = Set.of(
            Material.DIAMOND_SWORD,
            Material.DIAMOND_AXE,
            Material.BOW,
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS
    );

    private EnchantmentToolCatalog() {
        throw new UnsupportedOperationException(
                "Enchantment tool catalog cannot be instantiated"
        );
    }

    static boolean supports(Material material) {
        return SUPPORTED_ITEMS.contains(material);
    }

    static List<Option> optionsFor(Material material) {
        return enchantmentOptionsFor(material);
    }

    private static List<Option> enchantmentOptionsFor(Material material) {
        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        switch (material) {
            case DIAMOND_SWORD, DIAMOND_AXE -> {
                enchantments.put(Enchantment.DAMAGE_ALL, 4);
                enchantments.put(Enchantment.FIRE_ASPECT, 2);
                enchantments.put(Enchantment.KNOCKBACK, 2);
                enchantments.put(Enchantment.DURABILITY, 3);
            }
            case BOW -> {
                enchantments.put(Enchantment.ARROW_DAMAGE, 5);
                enchantments.put(Enchantment.ARROW_KNOCKBACK, 2);
                enchantments.put(Enchantment.ARROW_FIRE, 1);
                enchantments.put(Enchantment.DURABILITY, 3);
            }
            case DIAMOND_HELMET -> {
                enchantments.put(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
                enchantments.put(Enchantment.DURABILITY, 3);
                enchantments.put(Enchantment.WATER_WORKER, 1);
                enchantments.put(Enchantment.OXYGEN, 3);
            }
            case DIAMOND_CHESTPLATE -> {
                enchantments.put(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
                enchantments.put(Enchantment.DURABILITY, 3);
                enchantments.put(Enchantment.THORNS, 3);
            }
            case DIAMOND_LEGGINGS -> {
                enchantments.put(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
                enchantments.put(Enchantment.DURABILITY, 3);
            }
            case DIAMOND_BOOTS -> {
                enchantments.put(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
                enchantments.put(Enchantment.DURABILITY, 3);
                enchantments.put(Enchantment.DEPTH_STRIDER, 3);
                enchantments.put(Enchantment.PROTECTION_FALL, 4);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported enchanter material " + material
            );
        }
        int[] slots = enchantments.size() == 2
                ? new int[]{11, 15}
                : enchantments.size() == 3
                ? new int[]{10, 13, 16}
                : new int[]{10, 12, 14, 16};
        List<Option> options = new ArrayList<>(enchantments.size());
        int index = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            options.add(new Option(
                    slots[index++],
                    itemSlotKey(material)
                            + "::"
                            + enchantmentKey(entry.getKey()),
                    entry.getKey(),
                    entry.getValue()
            ));
        }
        return List.copyOf(options);
    }

    private static String itemSlotKey(Material material) {
        return switch (material) {
            case DIAMOND_SWORD -> "sword";
            case DIAMOND_AXE -> "axe";
            case BOW -> "bow";
            case DIAMOND_HELMET -> "helmet";
            case DIAMOND_CHESTPLATE -> "chestplate";
            case DIAMOND_LEGGINGS -> "leggings";
            case DIAMOND_BOOTS -> "boots";
            default -> throw new IllegalArgumentException(
                    "Unsupported enchanter material " + material
            );
        };
    }

    private static String enchantmentKey(Enchantment enchantment) {
        if (enchantment == Enchantment.DAMAGE_ALL) {
            return "sharpness";
        }
        if (enchantment == Enchantment.FIRE_ASPECT) {
            return "fire_aspect";
        }
        if (enchantment == Enchantment.KNOCKBACK) {
            return "knockback";
        }
        if (enchantment == Enchantment.DURABILITY) {
            return "durability";
        }
        if (enchantment == Enchantment.ARROW_DAMAGE) {
            return "power";
        }
        if (enchantment == Enchantment.ARROW_KNOCKBACK) {
            return "punch";
        }
        if (enchantment == Enchantment.ARROW_FIRE) {
            return "flame";
        }
        if (enchantment == Enchantment.PROTECTION_ENVIRONMENTAL) {
            return "protection";
        }
        if (enchantment == Enchantment.WATER_WORKER) {
            return "aqua_affinity";
        }
        if (enchantment == Enchantment.OXYGEN) {
            return "respiration";
        }
        if (enchantment == Enchantment.PROTECTION_FALL) {
            return "feather_falling";
        }
        if (enchantment == Enchantment.THORNS) {
            return "thorns";
        }
        if (enchantment == Enchantment.DEPTH_STRIDER) {
            return "depth_strider";
        }
        throw new IllegalArgumentException(
                "Unsupported enchantment tool option ID " + enchantment.getId()
        );
    }

    record Option(
            int slot,
            String offerKey,
            Enchantment enchantment,
            int level
    ) {
        ItemStack displayItem(EnchantmentToolAction action) {
            return action.optionItem().create(
                    Placeholder.unparsed("enchantment", displayName()),
                    Placeholder.unparsed("level", roman(level))
            );
        }

        String displayName() {
            return EnchantmentDisplayNames.displayName(enchantment);
        }

        private static String roman(int value) {
            return switch (value) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                default -> Integer.toString(value);
            };
        }
    }
}
