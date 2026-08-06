package net.valoury.bloodstone.server;

import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectTarget;
import net.valoury.bloodstone.server.RandomBoxRewards.Rarity;
import net.valoury.bloodstone.server.model.BloodstoneRank;
import net.valoury.bloodstone.server.model.BloodstoneShopProduct;
import net.valoury.bloodstone.server.service.BloodstoneEffectAxeService;
import net.valoury.bloodstone.server.service.BloodstoneItemIdentityService;
import net.valoury.bloodstone.server.service.BloodstoneItemService;
import org.bukkit.Color;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneCatalogTest {

    @Test
    void randomBoxKeepsLegacyWeightedPool() {
        assertEquals(15, RandomBoxRewards.values().size());
        assertEquals(24, RandomBoxRewards.totalWeight());
        assertEquals(3, totalWeight(Rarity.LEGENDARY));
        assertEquals(14, totalWeight(Rarity.RARE));
        assertEquals(7, totalWeight(Rarity.COMMON));
        assertEquals(1.0D,
                RandomBoxRewards.values().stream()
                        .mapToDouble(RandomBoxRewards.RandomBoxReward::probability)
                        .sum(),
                0.000_000_1D);
        assertEquals(Map.ofEntries(
                        Map.entry("golden_apple", 3),
                        Map.entry("protection_helmet", 2),
                        Map.entry("protection_chestplate", 2),
                        Map.entry("protection_leggings", 2),
                        Map.entry("protection_boots", 2),
                        Map.entry("strong_sword", 2),
                        Map.entry("strong_axe", 2),
                        Map.entry("strong_bow", 2),
                        Map.entry("unbreaking_sword", 1),
                        Map.entry("unbreaking_axe", 1),
                        Map.entry("unbreaking_helmet", 1),
                        Map.entry("unbreaking_chestplate", 1),
                        Map.entry("unbreaking_leggings", 1),
                        Map.entry("unbreaking_boots", 1),
                        Map.entry("unbreaking_bow", 1)
                ),
                RandomBoxRewards.values().stream().collect(Collectors.toMap(
                        RandomBoxRewards.RandomBoxReward::id,
                        RandomBoxRewards.RandomBoxReward::weight
        )));
        assertEquals("golden_apple", RandomBoxRewards.GOLDEN_APPLE.id());
        assertEquals(1, RandomBoxRewards.GOLDEN_APPLE.data());
        assertEquals(org.bukkit.Material.GOLDEN_APPLE,
                RandomBoxRewards.GOLDEN_APPLE.material());
        assertFalse(RandomBoxRewards.values().stream()
                .anyMatch(reward -> reward.id().contains("blessed")
                        || reward.material() == org.bukkit.Material.PRISMARINE_SHARD));
    }

    @Test
    void effectAxesKeepRankedCostsDurabilityAndEffects() {
        assertEquals(6, EffectAxeDefinitions.values().size());
        Map<String, Integer> valorianPrices = Map.of(
                "speed", 16,
                "strength", 32,
                "wither", 24,
                "blindness", 16,
                "weakness", 16,
                "poison", 24
        );
        for (EffectAxeDefinitions.EffectAxeDefinition definition
                : EffectAxeDefinitions.values()) {
            int valorianPrice = valorianPrices.get(definition.id());
            assertEquals(
                    valorianPrice,
                    definition.bloodAlloyCost(BloodstoneRank.VALORIAN)
            );
            assertEquals(
                    valorianPrice * 5 / 4,
                    definition.bloodAlloyCost(BloodstoneRank.ARCHON)
            );
            assertEquals(
                    valorianPrice * 3 / 2,
                    definition.bloodAlloyCost(BloodstoneRank.CAVALIER)
            );
            assertEquals(
                    valorianPrice * 2,
                    definition.bloodAlloyCost(BloodstoneRank.LEGATE)
            );
        }

        assertEquals(Duration.ofSeconds(8), EffectAxeDefinitions.SPEED.duration());
        assertEquals(0, EffectAxeDefinitions.SPEED.amplifier());
        assertEquals(EffectTarget.SELF, EffectAxeDefinitions.SPEED.target());
        assertEquals(Duration.ofSeconds(8), EffectAxeDefinitions.STRENGTH.duration());
        assertEquals(1, EffectAxeDefinitions.STRENGTH.amplifier());
        assertEquals(EffectTarget.SELF, EffectAxeDefinitions.STRENGTH.target());
        assertTrue(EffectAxeDefinitions.values().stream()
                .filter(definition -> definition.target() == EffectTarget.VICTIM)
                .allMatch(definition -> definition.duration().equals(Duration.ofSeconds(6))
                        && definition.amplifier() == 2));
        assertEquals(Color.fromRGB(150, 37, 36),
                EffectAxeDefinitions.STRENGTH.particleColor());
        assertEquals(Color.fromRGB(126, 178, 202),
                EffectAxeDefinitions.SPEED.particleColor());
        assertEquals(Color.fromRGB(79, 150, 50),
                EffectAxeDefinitions.POISON.particleColor());
        assertEquals(Color.fromRGB(53, 42, 39),
                EffectAxeDefinitions.WITHER.particleColor());
        assertEquals(Color.fromRGB(92, 110, 131),
                EffectAxeDefinitions.WEAKNESS.particleColor());
        assertEquals(Color.fromRGB(31, 31, 35),
                EffectAxeDefinitions.BLINDNESS.particleColor());
        org.bukkit.inventory.ItemStack axe =
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_AXE);
        axe.setDurability((short) (axe.getType().getMaxDurability() - 3));
        BloodstoneEffectAxeService effectAxeService =
                new BloodstoneEffectAxeService(
                        new BloodstoneItemIdentityService()
                );
        assertFalse(effectAxeService.consumeUse(axe));
        assertEquals(1, effectAxeService.remainingDurability(axe));
        assertTrue(effectAxeService.consumeUse(axe));
        assertEquals(0, axe.getAmount());
    }

    @Test
    void combinedEffectAxesCoverEveryDistinctPairExactlyOnce() {
        assertEquals(15, CombinedEffectAxeDefinitions.values().size());
        Set<Set<String>> pairs = new HashSet<>();
        for (CombinedEffectAxeDefinitions.CombinedEffectAxeDefinition definition
                : CombinedEffectAxeDefinitions.values()) {
            assertEquals(2, definition.effects().size());
            assertTrue(pairs.add(definition.effects().stream()
                    .map(EffectAxeDefinitions.EffectAxeDefinition::id)
                    .collect(Collectors.toUnmodifiableSet())));
            assertEquals(
                    definition,
                    CombinedEffectAxeDefinitions.find(
                            definition.secondEffect(),
                            definition.firstEffect()
                    ).orElseThrow()
            );
        }
        assertEquals(15, pairs.size());
        assertEquals(Map.ofEntries(
                        Map.entry("speed_strength", "Berserker Axe"),
                        Map.entry("speed_wither", "Reaper Axe"),
                        Map.entry("speed_blindness", "Phantom Axe"),
                        Map.entry("speed_weakness", "Predator Axe"),
                        Map.entry("speed_poison", "Viper Axe"),
                        Map.entry("strength_wither", "Ruin Axe"),
                        Map.entry("strength_blindness", "Dread Axe"),
                        Map.entry("strength_weakness", "Tyrant Axe"),
                        Map.entry("strength_poison", "Venomfang Axe"),
                        Map.entry("wither_blindness", "Void Axe"),
                        Map.entry("wither_weakness", "Decay Axe"),
                        Map.entry("wither_poison", "Plague Axe"),
                        Map.entry("blindness_weakness", "Oppression Axe"),
                        Map.entry("blindness_poison", "Nightshade Axe"),
                        Map.entry("weakness_poison", "Affliction Axe")
                ),
                CombinedEffectAxeDefinitions.values().stream()
                        .collect(Collectors.toMap(
                                CombinedEffectAxeDefinitions
                                        .CombinedEffectAxeDefinition::id,
                                definition -> definition
                                        .displayNameTemplate()
                                        .replace("<dark_aqua>", "")
                                        .replace("</dark_aqua>", "")
                        ))
        );
    }

    @Test
    void rankEconomyAndResistanceDurationStayBound() {
        assertEquals(4, BloodstoneRank.LEGATE.bloodPerQualifyingHit());
        assertEquals(5, BloodstoneRank.CAVALIER.bloodPerQualifyingHit());
        assertEquals(6, BloodstoneRank.ARCHON.bloodPerQualifyingHit());
        assertEquals(7, BloodstoneRank.VALORIAN.bloodPerQualifyingHit());
        assertEquals(3_600, new BloodstoneItemService().createResistanceEffect().getDuration());
        assertEquals(Map.ofEntries(
                        Map.entry(BloodstoneShopProduct.SHARPNESS_IV_SWORD, 2),
                        Map.entry(BloodstoneShopProduct.SHARPNESS_V_SWORD, 4),
                        Map.entry(BloodstoneShopProduct.POWER_V_BOW, 4),
                        Map.entry(BloodstoneShopProduct.SHARPNESS_IV_AXE, 2),
                        Map.entry(BloodstoneShopProduct.SHARPNESS_V_AXE, 4),
                        Map.entry(BloodstoneShopProduct.PROTECTION_IV_HELMET, 1),
                        Map.entry(BloodstoneShopProduct.PROTECTION_IV_CHESTPLATE, 1),
                        Map.entry(BloodstoneShopProduct.PROTECTION_IV_LEGGINGS, 1),
                        Map.entry(BloodstoneShopProduct.PROTECTION_IV_BOOTS, 1),
                        Map.entry(BloodstoneShopProduct.GOLDEN_APPLE, 5),
                        Map.entry(BloodstoneShopProduct.STRENGTH_POTION, 3),
                        Map.entry(BloodstoneShopProduct.RESISTANCE_POTION, 2),
                        Map.entry(BloodstoneShopProduct.SPEED_POTION, 2),
                        Map.entry(BloodstoneShopProduct.FIRE_RESISTANCE_POTION, 1)
                ),
                java.util.Arrays.stream(BloodstoneShopProduct.values())
                        .collect(Collectors.toMap(
                        product -> product,
                        BloodstoneShopProduct::bloodAlloyCost
                        )));
        assertFalse(java.util.Arrays.stream(BloodstoneShopProduct.values())
                .anyMatch(product -> product.name().contains("BLESSED")
                        || product.name().contains("PRISMARINE")));
    }

    @Test
    void storageIconsReflectWhetherTheStorageIsAvailable() {
        assertEquals(
                Material.STORAGE_MINECART,
                BloodstoneServerConstants.STORAGE_UNLOCKED_ITEM.material()
        );
        assertEquals(
                Material.STORAGE_MINECART,
                BloodstoneServerConstants.EXTRA_STORAGE_UNLOCKED_ITEM.material()
        );
        assertEquals(
                Material.MINECART,
                BloodstoneServerConstants.STORAGE_LOCKED_ITEM.material()
        );
        assertEquals(
                Material.MINECART,
                BloodstoneServerConstants.EXTRA_STORAGE_LOCKED_ITEM.material()
        );
        assertEquals(
                Material.MINECART,
                BloodstoneServerConstants.GUILD_STASH_ITEM.material()
        );
    }

    private int totalWeight(Rarity rarity) {
        return RandomBoxRewards.values().stream()
                .filter(reward -> reward.rarity() == rarity)
                .mapToInt(RandomBoxRewards.RandomBoxReward::weight)
                .sum();
    }
}
