package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.CombinedEffectAxeDefinitions;
import net.valoury.bloodstone.server.CombinedEffectAxeDefinitions.CombinedEffectAxeDefinition;
import net.valoury.bloodstone.server.EffectAxeDefinitions;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectAxeDefinition;
import net.valoury.bloodstone.server.EffectAxeItemDefinition;
import net.valoury.bloodstone.server.model.BloodstoneItemClassification;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BloodstoneEffectAxeService {

    private static final String EFFECT_AXE_ID_PREFIX = "effect_axe.";
    private static final int DURABILITY_COST_PER_HIT = 2;
    private static final int BASE_SHARPNESS_LEVEL = 3;
    private static final int FUSED_SHARPNESS_LEVEL = 4;
    private static final int UNBREAKING_LEVEL = 1;

    private final BloodstoneItemIdentityService itemIdentity;

    public BloodstoneEffectAxeService(
            BloodstoneItemIdentityService itemIdentity
    ) {
        this.itemIdentity = Objects.requireNonNull(
                itemIdentity,
                "Item identity cannot be null"
        );
    }

    public void validateRuntime() {
        EffectAxeDefinition speedDefinition = EffectAxeDefinitions.SPEED;
        ItemStack effectAxe = create(speedDefinition);
        if (definition(effectAxe).filter(speedDefinition::equals).isEmpty()
                || effectAxe.getEnchantmentLevel(Enchantment.DAMAGE_ALL)
                != BASE_SHARPNESS_LEVEL
                || effectAxe.getEnchantmentLevel(Enchantment.DURABILITY)
                != UNBREAKING_LEVEL
                || !effectAxe.hasItemMeta()
                || !effectAxe.getItemMeta().hasLore()
                || !effectAxe.getItemMeta().lore().contains(
                BloodstoneItemClassification.SOULBOUND.lore())) {
            throw new IllegalStateException(
                    "Effect Axe identity or presentation is unavailable"
            );
        }
        ItemStack fusedEffectAxe = create(
                CombinedEffectAxeDefinitions.BERSERKER
        );
        if (fusedEffectAxe.getEnchantmentLevel(Enchantment.DAMAGE_ALL)
                != FUSED_SHARPNESS_LEVEL) {
            throw new IllegalStateException(
                    "Fused Effect Axe Sharpness level is invalid"
            );
        }
    }

    public @NonNull ItemStack create(
            @NonNull EffectAxeItemDefinition definition
    ) {
        Objects.requireNonNull(definition, "Definition cannot be null");
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta itemMeta = axe.getItemMeta();
        itemMeta.displayName(BloodstoneText.deserialize(
                definition.displayNameTemplate()
        ));
        List<Component> lore = new ArrayList<>();
        for (EffectAxeDefinition effect : definition.effects()) {
            lore.add(BloodstoneText.deserialize(
                    effect.effectLoreTemplate()
            ));
        }
        lore.add(BloodstoneItemClassification.SOULBOUND.lore());
        itemMeta.lore(List.copyOf(lore));
        axe.setItemMeta(itemMeta);
        axe.addUnsafeEnchantment(
                Enchantment.DAMAGE_ALL,
                sharpnessLevel(definition)
        );
        axe.addUnsafeEnchantment(
                Enchantment.DURABILITY,
                UNBREAKING_LEVEL
        );
        return itemIdentity.withInternalItemId(
                axe,
                EFFECT_AXE_ID_PREFIX + definition.id()
        );
    }

    public @NonNull Optional<EffectAxeItemDefinition> definition(
            ItemStack item
    ) {
        return itemIdentity.internalItemId(item)
                .filter(itemId -> itemId.startsWith(EFFECT_AXE_ID_PREFIX))
                .flatMap(itemId -> {
                    String definitionId = itemId.substring(
                            EFFECT_AXE_ID_PREFIX.length()
                    );
                    Optional<EffectAxeDefinition> baseDefinition =
                            EffectAxeDefinitions.find(definitionId);
                    if (baseDefinition.isPresent()) {
                        return baseDefinition.map(
                                EffectAxeItemDefinition.class::cast
                        );
                    }
                    return CombinedEffectAxeDefinitions.find(definitionId)
                            .map(EffectAxeItemDefinition.class::cast);
                });
    }

    public @NonNull Optional<EffectAxeDefinition> baseDefinition(
            ItemStack item
    ) {
        return definition(item)
                .filter(EffectAxeDefinition.class::isInstance)
                .map(EffectAxeDefinition.class::cast);
    }

    public boolean isEffectAxe(ItemStack item) {
        return definition(item).isPresent();
    }

    public int remainingUses(@NonNull ItemStack item) {
        int remainingDurability = remainingDurability(item);
        return (remainingDurability + DURABILITY_COST_PER_HIT - 1)
                / DURABILITY_COST_PER_HIT;
    }

    public void setRemainingUses(
            @NonNull ItemStack item,
            int remainingUses
    ) {
        requireDamageable(item);
        int maximumDurability = item.getType().getMaxDurability();
        int maximumUses = (maximumDurability + DURABILITY_COST_PER_HIT - 1)
                / DURABILITY_COST_PER_HIT;
        if (remainingUses < 1 || remainingUses > maximumUses) {
            throw new IllegalArgumentException(
                    "Remaining uses must be between 1 and " + maximumUses
            );
        }
        setRemainingDurability(
                item,
                Math.min(
                        maximumDurability,
                        remainingUses * DURABILITY_COST_PER_HIT
                )
        );
    }

    public void setRemainingDurability(
            @NonNull ItemStack item,
            int remainingDurability
    ) {
        requireDamageable(item);
        int maximumDurability = item.getType().getMaxDurability();
        if (remainingDurability < 1
                || remainingDurability > maximumDurability) {
            throw new IllegalArgumentException(
                    "Remaining durability must be between 1 and "
                            + maximumDurability
            );
        }
        item.setDurability((short) (
                maximumDurability - remainingDurability
        ));
    }

    public boolean consumeUse(@NonNull ItemStack item) {
        return damage(item, DURABILITY_COST_PER_HIT);
    }

    public int remainingDurability(@NonNull ItemStack item) {
        requireDamageable(item);
        return Math.max(
                0,
                item.getType().getMaxDurability() - item.getDurability()
        );
    }

    public boolean damage(
            @NonNull ItemStack item,
            int durabilityPoints
    ) {
        requireDamageable(item);
        if (durabilityPoints < 1) {
            throw new IllegalArgumentException(
                    "Durability damage must be positive"
            );
        }
        int remainingDurability = remainingDurability(item);
        if (durabilityPoints >= remainingDurability) {
            item.setAmount(0);
            return true;
        }
        item.setDurability((short) (
                item.getDurability() + durabilityPoints
        ));
        return false;
    }

    public static int sharpnessLevel(
            @NonNull EffectAxeItemDefinition definition
    ) {
        return isFused(definition)
                ? FUSED_SHARPNESS_LEVEL
                : BASE_SHARPNESS_LEVEL;
    }

    public static int unbreakingLevel() {
        return UNBREAKING_LEVEL;
    }

    public static boolean isFused(
            @NonNull EffectAxeItemDefinition definition
    ) {
        return definition instanceof CombinedEffectAxeDefinition;
    }

    private static void requireDamageable(ItemStack item) {
        Objects.requireNonNull(item, "Item cannot be null");
        if (item.getType() == Material.AIR || item.getAmount() < 1) {
            throw new IllegalArgumentException("Item must not be empty");
        }
        if (item.getType().getMaxDurability() < 1) {
            throw new IllegalArgumentException(
                    item.getType() + " does not have durability"
            );
        }
    }
}
