package net.valoury.bloodstone.server.model;

import org.jspecify.annotations.NonNull;

import java.util.List;

public enum BloodstoneShopProduct {
    SHARPNESS_IV_SWORD(
            2,
            List.of(
                    "<gray>Sharpness IV</gray>",
                    "<gray>Knockback II</gray>",
                    "<gray>Fire Aspect II</gray>"
            )
    ),
    SHARPNESS_V_SWORD(
            4,
            List.of(
                    "<gray>Sharpness V</gray>",
                    "<gray>Knockback II</gray>",
                    "<gray>Fire Aspect II</gray>"
            )
    ),
    POWER_V_BOW(
            4,
            List.of(
                    "<gray>Power V</gray>",
                    "<gray>Punch II</gray>",
                    "<gray>Flame I</gray>",
                    "<gray>Infinity I</gray>",
                    "<gray>Unbreaking III</gray>"
            )
    ),
    SHARPNESS_IV_AXE(
            2,
            List.of(
                    "<gray>Sharpness IV</gray>",
                    "<gray>Knockback II</gray>",
                    "<gray>Fire Aspect II</gray>"
            )
    ),
    SHARPNESS_V_AXE(
            4,
            List.of(
                    "<gray>Sharpness V</gray>",
                    "<gray>Knockback II</gray>",
                    "<gray>Fire Aspect II</gray>"
            )
    ),
    PROTECTION_IV_HELMET(
            1,
            List.of(
                    "<gray>Protection IV</gray>",
                    "<gray>Unbreaking III</gray>"
            )
    ),
    PROTECTION_IV_CHESTPLATE(
            1,
            List.of(
                    "<gray>Protection IV</gray>",
                    "<gray>Unbreaking III</gray>"
            )
    ),
    PROTECTION_IV_LEGGINGS(
            1,
            List.of(
                    "<gray>Protection IV</gray>",
                    "<gray>Unbreaking III</gray>"
            )
    ),
    PROTECTION_IV_BOOTS(
            1,
            List.of(
                    "<gray>Protection IV</gray>",
                    "<gray>Unbreaking III</gray>"
            )
    ),
    GOLDEN_APPLE(5, List.of()),
    STRENGTH_POTION(3, List.of("<gray>Strength I (03:00)</gray>")),
    RESISTANCE_POTION(2, List.of("<gray>Resistance (03:00)</gray>")),
    SPEED_POTION(2, List.of("<gray>Speed I (03:00)</gray>")),
    FIRE_RESISTANCE_POTION(
            1,
            List.of("<gray>Fire Resistance I (03:00)</gray>")
    );

    private final int bloodAlloyCost;
    private final List<String> menuLoreTemplates;

    BloodstoneShopProduct(
            int bloodAlloyCost,
            List<String> menuLoreTemplates
    ) {
        this.bloodAlloyCost = bloodAlloyCost;
        this.menuLoreTemplates = List.copyOf(menuLoreTemplates);
    }

    public int bloodAlloyCost() {
        return bloodAlloyCost;
    }

    public @NonNull List<String> menuLoreTemplates() {
        return menuLoreTemplates;
    }
}
