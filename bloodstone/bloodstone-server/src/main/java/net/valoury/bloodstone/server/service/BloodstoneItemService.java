package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.BloodstoneItemClassification;
import net.valoury.bloodstone.server.model.BloodstoneShopProduct;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BloodstoneItemService {

    private static final String RESISTANCE_POTION_DISPLAY_NAME = "<white>Potion of Resistance</white>";
    private static final String GOLDEN_APPLE_DISPLAY_NAME = "<dark_purple>Golden Apple</dark_purple>";
    private static final short ENCHANTED_GOLDEN_APPLE_DATA = 1;
    private static final String RESISTANCE_POTION_ID = "resistance_potion";
    private static final String ARTIFICIAL_LAPIS_ID = "artificial_lapis";
    private static final int RESISTANCE_DURATION_TICKS = 3 * 60 * 20;

    private final BloodstoneItemIdentityService itemIdentity;
    private final BloodstoneEffectAxeService effectAxeService;

    public BloodstoneItemService() {
        this(new BloodstoneItemIdentityService());
    }

    public BloodstoneItemService(
            BloodstoneItemIdentityService itemIdentity
    ) {
        this(itemIdentity, new BloodstoneEffectAxeService(itemIdentity));
    }

    public BloodstoneItemService(
            BloodstoneItemIdentityService itemIdentity,
            BloodstoneEffectAxeService effectAxeService
    ) {
        this.itemIdentity = Objects.requireNonNull(
                itemIdentity,
                "Item identity cannot be null"
        );
        this.effectAxeService = Objects.requireNonNull(
                effectAxeService,
                "Effect Axe service cannot be null"
        );
    }

    public void validateRuntime() {
        ItemStack inclusive = createInclusiveItem(Material.DIAMOND_SWORD, 1);
        ItemStack exclusive = classify(
                inclusive,
                BloodstoneItemClassification.EXCLUSIVE
        );
        if (!isInclusive(inclusive)
                || isInclusive(removeClassification(inclusive))
                || !isExclusive(exclusive)
                || !exclusive.getItemMeta().hasLore()
                || exclusive.getItemMeta().lore().contains(
                BloodstoneItemClassification.INCLUSIVE.lore())
                || !exclusive.getItemMeta().lore().contains(
                BloodstoneItemClassification.EXCLUSIVE.lore())) {
            throw new IllegalStateException(
                    "Item classification ids or lore could not be replaced"
            );
        }

        UUID operationId = UUID.randomUUID();
        ItemStack recoverable = itemIdentity.withOperationId(
                inclusive,
                operationId
        );
        if (!isInclusive(recoverable)
                || itemIdentity.operationId(recoverable)
                .filter(operationId::equals).isEmpty()
                || !recoverable.hasItemMeta()
                || !recoverable.getItemMeta().hasLore()
                || !recoverable.getItemMeta().lore().contains(
                BloodstoneItemClassification.INCLUSIVE.lore())) {
            throw new IllegalStateException(
                    "Recovery operation tagging did not preserve item identity or lore"
            );
        }

        try {
            ItemStack serialized = BukkitItemSerialization.deserializeItem(
                    BukkitItemSerialization.serializeItem(recoverable)
            );
            if (!isInclusive(serialized)
                    || itemIdentity.operationId(serialized)
                    .filter(operationId::equals).isEmpty()) {
                throw new IllegalStateException("Private item ids did not survive Carbon serialization");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Carbon item serialization is unavailable", exception);
        }

        if (createResistanceEffect().getDuration() != RESISTANCE_DURATION_TICKS) {
            throw new IllegalStateException("Resistance effect duration does not match the item contract");
        }
    }

    public @NonNull ItemStack createInclusiveItem(@NonNull Material material, int amount) {
        return classify(
                new ItemStack(material, checkedStackAmount(material, amount)),
                BloodstoneItemClassification.INCLUSIVE
        );
    }

    public @NonNull ItemStack createExclusiveItem(@NonNull Material material, int amount) {
        return classify(
                new ItemStack(material, checkedStackAmount(material, amount)),
                BloodstoneItemClassification.EXCLUSIVE
        );
    }

    public @NonNull ItemStack createSoulboundItem(@NonNull Material material, int amount) {
        return classify(
                new ItemStack(material, checkedStackAmount(material, amount)),
                BloodstoneItemClassification.SOULBOUND
        );
    }

    public @NonNull ItemStack classify(
            @NonNull ItemStack item,
            @NonNull BloodstoneItemClassification classification
    ) {
        requireUsableItem(item);
        ItemStack classified = item.clone();
        ItemMeta itemMeta = classified.getItemMeta();
        List<Component> existingLore = itemMeta.hasLore()
                ? new ArrayList<>(itemMeta.lore())
                : new ArrayList<>();
        itemMeta.lore(replaceClassificationLore(existingLore, classification));
        classified.setItemMeta(itemMeta);
        return itemIdentity.withInternalItemId(
                classified,
                classification.internalId()
        );
    }

    static @NonNull List<Component> replaceClassificationLore(
            @NonNull List<Component> existingLore,
            @NonNull BloodstoneItemClassification classification
    ) {
        List<Component> updatedLore = new ArrayList<>(existingLore);
        for (BloodstoneItemClassification existingClassification
                : BloodstoneItemClassification.values()) {
            updatedLore.removeIf(existingClassification.lore()::equals);
        }
        updatedLore.add(classification.lore());
        return List.copyOf(updatedLore);
    }

    /**
     * Removes an Inclusive or Exclusive classification when explicitly requested.
     * Soulbound and non-classification item ids are deliberately preserved.
     */
    public @NonNull ItemStack removeClassification(@NonNull ItemStack item) {
        requireUsableItem(item);
        Optional<String> itemId = itemIdentity.internalItemId(item);
        BloodstoneItemClassification removableClassification;
        if (itemId.filter(BloodstoneItemClassification.INCLUSIVE
                .internalId()::equals).isPresent()) {
            removableClassification = BloodstoneItemClassification.INCLUSIVE;
        } else if (itemId.filter(BloodstoneItemClassification.EXCLUSIVE
                .internalId()::equals).isPresent()) {
            removableClassification = BloodstoneItemClassification.EXCLUSIVE;
        } else {
            return item.clone();
        }

        ItemStack unclassified = item.clone();
        ItemMeta itemMeta = unclassified.getItemMeta();
        if (itemMeta.hasLore()) {
            List<Component> lore = new ArrayList<>(itemMeta.lore());
            lore.removeIf(removableClassification.lore()::equals);
            itemMeta.lore(lore);
            unclassified.setItemMeta(itemMeta);
        }
        return itemIdentity.withoutInternalItemId(unclassified);
    }

    public boolean isInclusive(ItemStack item) {
        return itemIdentity.hasInternalItemId(
                item,
                BloodstoneItemClassification.INCLUSIVE.internalId()
        );
    }

    public boolean isExclusive(ItemStack item) {
        return itemIdentity.hasInternalItemId(
                item,
                BloodstoneItemClassification.EXCLUSIVE.internalId()
        );
    }

    public boolean isSoulbound(ItemStack item) {
        Optional<String> itemId = itemIdentity.internalItemId(item);
        return itemId.filter(BloodstoneItemClassification.SOULBOUND
                .internalId()::equals).isPresent()
                || effectAxeService.isEffectAxe(item);
    }

    public @NonNull Optional<BloodstoneItemClassification> classification(
            ItemStack item
    ) {
        return itemIdentity.internalItemId(item).flatMap(id -> {
            for (BloodstoneItemClassification classification
                    : BloodstoneItemClassification.values()) {
                if (classification.internalId().equals(id)) {
                    return Optional.of(classification);
                }
            }
            return effectAxeService.isEffectAxe(item)
                    ? Optional.of(BloodstoneItemClassification.SOULBOUND)
                    : Optional.empty();
        });
    }

    public boolean isRestrictedFromModification(ItemStack item) {
        return classification(item)
                .filter(BloodstoneItemClassification
                        ::isRestrictedFromModification)
                .isPresent();
    }

    public @NonNull ItemStack createResistancePotion() {
        ItemStack potion = new ItemStack(Material.POTION, 1, (short) 0);
        ItemMeta itemMeta = potion.getItemMeta();
        itemMeta.displayName(BloodstoneText.deserialize(
                RESISTANCE_POTION_DISPLAY_NAME
        ));
        itemMeta.lore(List.of(BloodstoneText.deserialize(
                "<gray>Resistance (03:00)</gray>"
        )));
        potion.setItemMeta(itemMeta);
        return itemIdentity.withInternalItemId(
                potion,
                RESISTANCE_POTION_ID
        );
    }

    public boolean isResistancePotion(ItemStack item) {
        return itemIdentity.hasInternalItemId(item, RESISTANCE_POTION_ID);
    }

    public @NonNull PotionEffect createResistanceEffect() {
        return new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, RESISTANCE_DURATION_TICKS, 0);
    }

    public @NonNull ItemStack createArtificialLapis(int amount) {
        ItemStack lapis = new ItemStack(
                Material.INK_SACK,
                checkedStackAmount(Material.INK_SACK, amount),
                (short) 4
        );
        return itemIdentity.withInternalItemId(
                lapis,
                ARTIFICIAL_LAPIS_ID
        );
    }

    public boolean isArtificialLapis(ItemStack item) {
        return itemIdentity.hasInternalItemId(item, ARTIFICIAL_LAPIS_ID);
    }

    public @NonNull ItemStack createShopItem(
            @NonNull BloodstoneShopProduct product
    ) {
        return switch (product) {
            case SHARPNESS_IV_SWORD -> enchantedItem(
                    Material.DIAMOND_SWORD,
                    Map.of(
                            Enchantment.DAMAGE_ALL, 4,
                            Enchantment.KNOCKBACK, 2,
                            Enchantment.FIRE_ASPECT, 2
                    )
            );
            case SHARPNESS_V_SWORD -> enchantedItem(
                    Material.DIAMOND_SWORD,
                    Map.of(
                            Enchantment.DAMAGE_ALL, 5,
                            Enchantment.KNOCKBACK, 2,
                            Enchantment.FIRE_ASPECT, 2
                    )
            );
            case POWER_V_BOW -> enchantedItem(
                    Material.BOW,
                    Map.of(
                            Enchantment.ARROW_DAMAGE, 5,
                            Enchantment.ARROW_KNOCKBACK, 2,
                            Enchantment.ARROW_FIRE, 1,
                            Enchantment.ARROW_INFINITE, 1,
                            Enchantment.DURABILITY, 3
                    )
            );
            case SHARPNESS_IV_AXE -> enchantedItem(
                    Material.DIAMOND_AXE,
                    Map.of(
                            Enchantment.DAMAGE_ALL, 4,
                            Enchantment.KNOCKBACK, 2,
                            Enchantment.FIRE_ASPECT, 2
                    )
            );
            case SHARPNESS_V_AXE -> enchantedItem(
                    Material.DIAMOND_AXE,
                    Map.of(
                            Enchantment.DAMAGE_ALL, 5,
                            Enchantment.KNOCKBACK, 2,
                            Enchantment.FIRE_ASPECT, 2
                    )
            );
            case PROTECTION_IV_HELMET -> shopArmor(Material.DIAMOND_HELMET);
            case PROTECTION_IV_CHESTPLATE -> shopArmor(Material.DIAMOND_CHESTPLATE);
            case PROTECTION_IV_LEGGINGS -> shopArmor(Material.DIAMOND_LEGGINGS);
            case PROTECTION_IV_BOOTS -> shopArmor(Material.DIAMOND_BOOTS);
            case GOLDEN_APPLE -> {
                ItemStack apple = new ItemStack(
                        Material.GOLDEN_APPLE,
                        1,
                        ENCHANTED_GOLDEN_APPLE_DATA
                );
                ItemMeta itemMeta = apple.getItemMeta();
                itemMeta.displayName(BloodstoneText.deserialize(
                        GOLDEN_APPLE_DISPLAY_NAME
                ));
                apple.setItemMeta(itemMeta);
                yield apple;
            }
            case STRENGTH_POTION -> new Potion(PotionType.STRENGTH).toItemStack(1);
            case RESISTANCE_POTION -> createResistancePotion();
            case SPEED_POTION -> new Potion(PotionType.SPEED).toItemStack(1);
            case FIRE_RESISTANCE_POTION ->
                    new Potion(PotionType.FIRE_RESISTANCE).toItemStack(1);
        };
    }

    private ItemStack enchantedItem(Material material, Map<Enchantment, Integer> enchantments) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantments(enchantments);
        return item;
    }

    private ItemStack shopArmor(Material material) {
        return enchantedItem(
                material,
                Map.of(Enchantment.PROTECTION_ENVIRONMENTAL, 4, Enchantment.DURABILITY, 3)
        );
    }

    private int checkedStackAmount(Material material, int amount) {
        requirePositiveAmount(amount);
        if (amount > material.getMaxStackSize()) {
            throw new IllegalArgumentException(
                    "Amount exceeds " + material + " maximum stack size of " + material.getMaxStackSize()
            );
        }
        return amount;
    }

    private void requirePositiveAmount(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private void requireUsableItem(ItemStack item) {
        if (item.getType() == Material.AIR || item.getAmount() < 1) {
            throw new IllegalArgumentException("Cannot classify an empty item");
        }
    }

}
