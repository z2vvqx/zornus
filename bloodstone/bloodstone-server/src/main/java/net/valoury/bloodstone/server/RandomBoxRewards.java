package net.valoury.bloodstone.server;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

public final class RandomBoxRewards {

    private static final int ENCHANTED_GOLDEN_APPLE_DATA = 1;

    public static final RandomBoxReward GOLDEN_APPLE = reward(
            "golden_apple",
            Material.GOLDEN_APPLE,
            ENCHANTED_GOLDEN_APPLE_DATA,
            "<dark_purple>Golden Apple</dark_purple>",
            3,
            Rarity.LEGENDARY
    );

    public static final RandomBoxReward PROTECTION_HELMET = enchantedReward(
            "protection_helmet", Material.DIAMOND_HELMET, 2, Rarity.RARE,
            Map.of(Enchantment.PROTECTION_ENVIRONMENTAL, 4, Enchantment.DURABILITY, 3)
    );
    public static final RandomBoxReward PROTECTION_CHESTPLATE = enchantedReward(
            "protection_chestplate", Material.DIAMOND_CHESTPLATE, 2, Rarity.RARE,
            Map.of(Enchantment.PROTECTION_ENVIRONMENTAL, 4, Enchantment.DURABILITY, 3)
    );
    public static final RandomBoxReward PROTECTION_LEGGINGS = enchantedReward(
            "protection_leggings", Material.DIAMOND_LEGGINGS, 2, Rarity.RARE,
            Map.of(Enchantment.PROTECTION_ENVIRONMENTAL, 4, Enchantment.DURABILITY, 3)
    );
    public static final RandomBoxReward PROTECTION_BOOTS = enchantedReward(
            "protection_boots", Material.DIAMOND_BOOTS, 2, Rarity.RARE,
            Map.of(Enchantment.PROTECTION_ENVIRONMENTAL, 4, Enchantment.DURABILITY, 3)
    );

    public static final RandomBoxReward STRONG_SWORD = enchantedReward(
            "strong_sword", Material.DIAMOND_SWORD, 2, Rarity.RARE,
            Map.of(
                    Enchantment.DAMAGE_ALL, 4,
                    Enchantment.FIRE_ASPECT, 1,
                    Enchantment.KNOCKBACK, 1
            )
    );
    public static final RandomBoxReward STRONG_AXE = enchantedReward(
            "strong_axe", Material.DIAMOND_AXE, 2, Rarity.RARE,
            Map.of(
                    Enchantment.DAMAGE_ALL, 4,
                    Enchantment.FIRE_ASPECT, 1,
                    Enchantment.KNOCKBACK, 1
            )
    );
    public static final RandomBoxReward STRONG_BOW = enchantedReward(
            "strong_bow", Material.BOW, 2, Rarity.RARE,
            Map.of(Enchantment.ARROW_DAMAGE, 4, Enchantment.ARROW_FIRE, 1)
    );

    public static final RandomBoxReward UNBREAKING_SWORD = enchantedReward(
            "unbreaking_sword", Material.DIAMOND_SWORD, 1, Rarity.COMMON,
            Map.of(Enchantment.DURABILITY, 2)
    );
    public static final RandomBoxReward UNBREAKING_AXE = enchantedReward(
            "unbreaking_axe", Material.DIAMOND_AXE, 1, Rarity.COMMON,
            Map.of(Enchantment.DURABILITY, 1)
    );
    public static final RandomBoxReward UNBREAKING_HELMET = enchantedReward(
            "unbreaking_helmet", Material.DIAMOND_HELMET, 1, Rarity.COMMON,
            Map.of(Enchantment.DURABILITY, 2)
    );
    public static final RandomBoxReward UNBREAKING_CHESTPLATE = enchantedReward(
            "unbreaking_chestplate", Material.DIAMOND_CHESTPLATE, 1, Rarity.COMMON,
            Map.of(Enchantment.DURABILITY, 1)
    );
    public static final RandomBoxReward UNBREAKING_LEGGINGS = enchantedReward(
            "unbreaking_leggings", Material.DIAMOND_LEGGINGS, 1, Rarity.COMMON,
            Map.of(Enchantment.DURABILITY, 3)
    );
    public static final RandomBoxReward UNBREAKING_BOOTS = enchantedReward(
            "unbreaking_boots", Material.DIAMOND_BOOTS, 1, Rarity.COMMON,
            Map.of(Enchantment.DURABILITY, 2)
    );
    public static final RandomBoxReward UNBREAKING_BOW = enchantedReward(
            "unbreaking_bow", Material.BOW, 1, Rarity.COMMON,
            Map.of(Enchantment.DURABILITY, 3)
    );

    private static final List<RandomBoxReward> REWARDS = List.of(
            GOLDEN_APPLE,
            PROTECTION_HELMET,
            PROTECTION_CHESTPLATE,
            PROTECTION_LEGGINGS,
            PROTECTION_BOOTS,
            STRONG_SWORD,
            STRONG_AXE,
            STRONG_BOW,
            UNBREAKING_SWORD,
            UNBREAKING_AXE,
            UNBREAKING_HELMET,
            UNBREAKING_CHESTPLATE,
            UNBREAKING_LEGGINGS,
            UNBREAKING_BOOTS,
            UNBREAKING_BOW
    );
    private static final Map<String, RandomBoxReward> REWARDS_BY_ID = createRewardIndex();
    private static final int TOTAL_WEIGHT = REWARDS.stream().mapToInt(RandomBoxReward::weight).sum();
    private static final SecureRandom UNPREDICTABLE_RANDOM = new SecureRandom();

    static {
        if (TOTAL_WEIGHT != 24) {
            throw new IllegalStateException("Random Box reward pool must contain exactly 24 weighted slots");
        }
    }

    private RandomBoxRewards() {
    }

    public static @NonNull List<RandomBoxReward> values() {
        return REWARDS;
    }

    public static @NonNull Optional<RandomBoxReward> find(@NonNull String id) {
        return Optional.ofNullable(REWARDS_BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    public static int totalWeight() {
        return TOTAL_WEIGHT;
    }

    public static @NonNull RandomBoxReward roll() {
        return roll(UNPREDICTABLE_RANDOM);
    }

    public static @NonNull RandomBoxReward roll(@NonNull RandomGenerator random) {
        int selectedSlot = random.nextInt(TOTAL_WEIGHT);
        for (RandomBoxReward reward : REWARDS) {
            selectedSlot -= reward.weight();
            if (selectedSlot < 0) {
                return reward;
            }
        }
        throw new IllegalStateException("Random Box reward weights are inconsistent");
    }

    private static RandomBoxReward reward(
            String id,
            Material material,
            int data,
            @Nullable String displayNameTemplate,
            int weight,
            Rarity rarity
    ) {
        return new RandomBoxReward(
                id,
                material,
                data,
                displayNameTemplate,
                Map.of(),
                weight,
                rarity,
                material
        );
    }

    private static RandomBoxReward enchantedReward(
            String id,
            Material material,
            int weight,
            Rarity rarity,
            Map<Enchantment, Integer> enchantments
    ) {
        return new RandomBoxReward(
                id,
                material,
                0,
                null,
                enchantments,
                weight,
                rarity,
                material
        );
    }

    private static Map<String, RandomBoxReward> createRewardIndex() {
        Map<String, RandomBoxReward> rewardsById = new LinkedHashMap<>();
        for (RandomBoxReward reward : REWARDS) {
            RandomBoxReward previous = rewardsById.put(reward.id(), reward);
            if (previous != null) {
                throw new IllegalStateException("Duplicate Random Box reward id: " + reward.id());
            }
        }
        return Map.copyOf(rewardsById);
    }

    public record RandomBoxReward(
            @NonNull String id,
            @NonNull Material material,
            int data,
            @Nullable String displayNameTemplate,
            @NonNull Map<Enchantment, Integer> enchantments,
            int weight,
            @NonNull Rarity rarity,
            @NonNull Material displayMaterial
    ) {
        public RandomBoxReward {
            if (id.isBlank()) {
                throw new IllegalArgumentException("Random Box reward id cannot be blank");
            }
            id = id.toLowerCase(Locale.ROOT);
            if (data < 0 || data > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Item data is outside the supported range");
            }
            if (weight < 1) {
                throw new IllegalArgumentException("Random Box reward weight must be positive");
            }
            enchantments = Map.copyOf(enchantments);
        }

        public double probability() {
            return (double) weight / TOTAL_WEIGHT;
        }

        public @NonNull ItemStack createItem() {
            ItemStack item = new ItemStack(material, 1, (short) data);
            if (displayNameTemplate != null) {
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.displayName(BloodstoneText.deserialize(displayNameTemplate));
                item.setItemMeta(itemMeta);
            }
            item.addUnsafeEnchantments(enchantments);
            return item;
        }
    }

    public enum Rarity {
        COMMON,
        RARE,
        LEGENDARY
    }
}
