package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.CombinedEffectAxeDefinitions;
import net.valoury.bloodstone.server.EffectAxeDefinitions;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectAxeDefinition;
import net.valoury.bloodstone.server.EffectAxeItemDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public final class BloodstoneItemService {

    private static final String BLOOD_DISPLAY_NAME = "<white>Blood</white>";
    private static final String BLOOD_ALLOY_DISPLAY_NAME = "<white>Blood Alloy</white>";
    private static final String RESISTANCE_POTION_DISPLAY_NAME = "<white>Potion of Resistance</white>";
    private static final String GOLDEN_APPLE_DISPLAY_NAME = "<dark_purple>Golden Apple</dark_purple>";
    private static final short ENCHANTED_GOLDEN_APPLE_DATA = 1;
    private static final String BLOOD_ID = "blood";
    private static final String BLOOD_ALLOY_ID = "blood_alloy";
    private static final String RESISTANCE_POTION_ID = "resistance_potion";
    private static final String ARTIFICIAL_LAPIS_ID = "artificial_lapis";
    private static final String EFFECT_AXE_ID_PREFIX = "effect_axe.";
    private static final String INTERNAL_ITEM_ID_KEY = "valoury_bloodstone_item";
    private static final String OPERATION_ID_KEY = "valoury_bloodstone_operation";
    private static final int RESISTANCE_DURATION_TICKS = 3 * 60 * 20;
    private static final int EFFECT_AXE_DURABILITY_COST_PER_HIT = 2;
    private static final int EFFECT_AXE_UNBREAKING_LEVEL = 1;
    private static final List<String> EFFECT_AXE_MENU_LORE =
            List.of("<gray>Unbreaking I</gray>");

    private final UnsafeItemTags itemTags;

    public BloodstoneItemService() {
        this(new UnsafeItemTags());
    }

    BloodstoneItemService(UnsafeItemTags itemTags) {
        this.itemTags = itemTags;
    }

    public void validateRuntime() {
        ItemStack blood = createBlood(1);
        if (!isBlood(blood)
                || !blood.hasItemMeta()
                || !blood.getItemMeta().hasDisplayName()
                || !BloodstoneText.deserialize(BLOOD_DISPLAY_NAME)
                .equals(blood.getItemMeta().displayName())) {
            throw new IllegalStateException(
                    "Blood item id or presentation did not survive Carbon item conversion"
            );
        }

        ItemStack inclusive = createInclusiveItem(Material.DIAMOND_SWORD, 1);
        if (!isInclusive(inclusive) || isInclusive(removeClassification(inclusive))) {
            throw new IllegalStateException("Item classification ids could not be written and removed");
        }

        EffectAxeDefinition speedAxeDefinition = EffectAxeDefinitions.SPEED;
        ItemStack effectAxe = createEffectAxe(speedAxeDefinition);
        if (effectAxeDefinition(effectAxe).filter(speedAxeDefinition::equals).isEmpty()
                || effectAxe.getEnchantmentLevel(Enchantment.DURABILITY)
                != EFFECT_AXE_UNBREAKING_LEVEL
                || !effectAxe.hasItemMeta()
                || !effectAxe.getItemMeta().hasLore()
                || !effectAxe.getItemMeta().lore().contains(
                Classification.SOULBOUND.lore())) {
            throw new IllegalStateException(
                    "Effect Axe id or presentation did not survive Carbon item conversion"
            );
        }
        ItemStack selectedFuserDisplay = createEffectAxeFuserDisplay(
                speedAxeDefinition,
                true
        );
        ItemStack unselectedFuserDisplay = createEffectAxeFuserDisplay(
                speedAxeDefinition,
                false
        );
        Component unbreakingLore = BloodstoneText.deserialize(
                EFFECT_AXE_MENU_LORE.getFirst()
        );
        if (selectedFuserDisplay.getEnchantmentLevel(Enchantment.DURABILITY)
                != EFFECT_AXE_UNBREAKING_LEVEL
                || unselectedFuserDisplay.getEnchantmentLevel(
                Enchantment.DURABILITY) != 0
                || !selectedFuserDisplay.getItemMeta().lore()
                .contains(unbreakingLore)
                || !unselectedFuserDisplay.getItemMeta().lore()
                .contains(unbreakingLore)) {
            throw new IllegalStateException(
                    "Axe Fuser selection glint or Unbreaking lore is invalid"
            );
        }

        UUID operationId = UUID.randomUUID();
        ItemStack recoverable = withOperationId(blood, operationId);
        if (!isBlood(recoverable)
                || !operationId(recoverable).filter(operationId::equals).isPresent()
                || !recoverable.hasItemMeta()
                || !recoverable.getItemMeta().hasDisplayName()) {
            throw new IllegalStateException(
                    "Recovery operation tagging did not preserve private item ids"
            );
        }

        try {
            ItemStack serialized = BukkitItemSerialization.deserializeItem(
                    BukkitItemSerialization.serializeItem(recoverable)
            );
            if (!isBlood(serialized)
                    || !operationId(serialized).filter(operationId::equals).isPresent()) {
                throw new IllegalStateException("Private item ids did not survive Carbon serialization");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Carbon item serialization is unavailable", exception);
        }

        if (createResistanceEffect().getDuration() != RESISTANCE_DURATION_TICKS) {
            throw new IllegalStateException("Resistance effect duration does not match the item contract");
        }
    }

    public @NonNull ItemStack createBlood(int amount) {
        return createIdentifiedItem(
                Material.REDSTONE,
                amount,
                (short) 0,
                BLOOD_DISPLAY_NAME,
                BLOOD_ID
        );
    }

    public @NonNull ItemStack createBloodAlloy(int amount) {
        return createIdentifiedItem(
                Material.NETHER_BRICK_ITEM,
                amount,
                (short) 0,
                BLOOD_ALLOY_DISPLAY_NAME,
                BLOOD_ALLOY_ID
        );
    }

    public boolean isBlood(ItemStack item) {
        return hasInternalId(item, BLOOD_ID);
    }

    public boolean isBloodAlloy(ItemStack item) {
        return hasInternalId(item, BLOOD_ALLOY_ID);
    }

    public @NonNull ItemStack createInclusiveItem(@NonNull Material material, int amount) {
        return classify(new ItemStack(material, checkedStackAmount(material, amount)), Classification.INCLUSIVE);
    }

    public @NonNull ItemStack createExclusiveItem(@NonNull Material material, int amount) {
        return classify(new ItemStack(material, checkedStackAmount(material, amount)), Classification.EXCLUSIVE);
    }

    public @NonNull ItemStack createSoulboundItem(@NonNull Material material, int amount) {
        return classify(new ItemStack(material, checkedStackAmount(material, amount)), Classification.SOULBOUND);
    }

    public @NonNull ItemStack classify(@NonNull ItemStack item, @NonNull Classification classification) {
        requireUsableItem(item);
        ItemStack classified = item.clone();
        ItemMeta itemMeta = classified.getItemMeta();
        List<Component> lore = itemMeta.hasLore()
                ? new ArrayList<>(itemMeta.lore())
                : new ArrayList<>();
        if (!lore.contains(classification.lore())) {
            lore.add(classification.lore());
        }
        itemMeta.lore(lore);
        classified.setItemMeta(itemMeta);
        return itemTags.withString(classified, INTERNAL_ITEM_ID_KEY, classification.internalId());
    }

    /**
     * Removes an Inclusive or Exclusive classification before normal enchanting.
     * Soulbound and non-classification item ids are deliberately preserved.
     */
    public @NonNull ItemStack removeClassification(@NonNull ItemStack item) {
        requireUsableItem(item);
        Optional<String> itemId = internalItemId(item);
        Classification removableClassification;
        if (itemId.filter(Classification.INCLUSIVE.internalId()::equals).isPresent()) {
            removableClassification = Classification.INCLUSIVE;
        } else if (itemId.filter(Classification.EXCLUSIVE.internalId()::equals).isPresent()) {
            removableClassification = Classification.EXCLUSIVE;
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
        return itemTags.withString(unclassified, INTERNAL_ITEM_ID_KEY, "");
    }

    public boolean isInclusive(ItemStack item) {
        return hasInternalId(item, Classification.INCLUSIVE.internalId());
    }

    public boolean isExclusive(ItemStack item) {
        return hasInternalId(item, Classification.EXCLUSIVE.internalId());
    }

    public boolean isSoulbound(ItemStack item) {
        Optional<String> itemId = internalItemId(item);
        return itemId.filter(id -> Classification.SOULBOUND.internalId().equals(id)
                || id.startsWith(EFFECT_AXE_ID_PREFIX)).isPresent();
    }

    public @NonNull Optional<Classification> classification(ItemStack item) {
        return internalItemId(item).flatMap(id -> {
            for (Classification classification : Classification.values()) {
                if (classification.internalId().equals(id)) {
                    return Optional.of(classification);
                }
            }
            return id.startsWith(EFFECT_AXE_ID_PREFIX)
                    ? Optional.of(Classification.SOULBOUND)
                    : Optional.empty();
        });
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
        return itemTags.withString(potion, INTERNAL_ITEM_ID_KEY, RESISTANCE_POTION_ID);
    }

    public boolean isResistancePotion(ItemStack item) {
        return hasInternalId(item, RESISTANCE_POTION_ID);
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
        return itemTags.withString(lapis, INTERNAL_ITEM_ID_KEY, ARTIFICIAL_LAPIS_ID);
    }

    public boolean isArtificialLapis(ItemStack item) {
        return hasInternalId(item, ARTIFICIAL_LAPIS_ID);
    }

    public @NonNull ItemStack createEffectAxe(
            @NonNull EffectAxeItemDefinition definition
    ) {
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta itemMeta = axe.getItemMeta();
        itemMeta.displayName(BloodstoneText.deserialize(
                definition.displayNameTemplate()
        ));
        List<Component> lore = new ArrayList<>();
        for (EffectAxeDefinition effect : definition.effects()) {
            lore.add(BloodstoneText.deserialize(effect.effectLoreTemplate()));
        }
        lore.add(Classification.SOULBOUND.lore());
        itemMeta.lore(List.copyOf(lore));
        axe.setItemMeta(itemMeta);
        axe.addUnsafeEnchantment(
                Enchantment.DURABILITY,
                EFFECT_AXE_UNBREAKING_LEVEL
        );
        return itemTags.withString(axe, INTERNAL_ITEM_ID_KEY, EFFECT_AXE_ID_PREFIX + definition.id());
    }

    public @NonNull Optional<EffectAxeItemDefinition> effectAxeDefinition(ItemStack item) {
        return internalItemId(item)
                .filter(itemId -> itemId.startsWith(EFFECT_AXE_ID_PREFIX))
                .flatMap(itemId -> {
                    String definitionId =
                            itemId.substring(EFFECT_AXE_ID_PREFIX.length());
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

    public @NonNull Optional<EffectAxeDefinition> baseEffectAxeDefinition(
            ItemStack item
    ) {
        return effectAxeDefinition(item)
                .filter(EffectAxeDefinition.class::isInstance)
                .map(EffectAxeDefinition.class::cast);
    }

    public boolean isEffectAxe(ItemStack item) {
        return effectAxeDefinition(item).isPresent();
    }

    public int remainingUses(@NonNull ItemStack item) {
        int remainingDurability = remainingDurability(item);
        return (remainingDurability + EFFECT_AXE_DURABILITY_COST_PER_HIT - 1)
                / EFFECT_AXE_DURABILITY_COST_PER_HIT;
    }

    public void setRemainingUses(@NonNull ItemStack item, int remainingUses) {
        requireDamageable(item);
        int maximumDurability = item.getType().getMaxDurability();
        int maximumUses = (maximumDurability + EFFECT_AXE_DURABILITY_COST_PER_HIT - 1)
                / EFFECT_AXE_DURABILITY_COST_PER_HIT;
        if (remainingUses < 1 || remainingUses > maximumUses) {
            throw new IllegalArgumentException(
                    "Remaining uses must be between 1 and " + maximumUses
            );
        }
        int remainingDurability = Math.min(
                maximumDurability,
                remainingUses * EFFECT_AXE_DURABILITY_COST_PER_HIT
        );
        item.setDurability((short) (maximumDurability - remainingDurability));
    }

    public boolean consumeControlledUse(@NonNull ItemStack item) {
        return damageControlled(item, EFFECT_AXE_DURABILITY_COST_PER_HIT);
    }

    public int remainingDurability(@NonNull ItemStack item) {
        requireDamageable(item);
        return Math.max(0, item.getType().getMaxDurability() - item.getDurability());
    }

    public boolean damageControlled(@NonNull ItemStack item, int durabilityPoints) {
        requireDamageable(item);
        if (durabilityPoints < 1) {
            throw new IllegalArgumentException("Durability damage must be positive");
        }
        int remainingDurability = remainingDurability(item);
        if (durabilityPoints >= remainingDurability) {
            item.setAmount(0);
            return true;
        }
        item.setDurability((short) (item.getDurability() + durabilityPoints));
        return false;
    }

    public int countBlood(@NonNull Inventory inventory) {
        return countIdentifiedItems(inventory, BLOOD_ID);
    }

    public int countBloodAlloy(@NonNull Inventory inventory) {
        return countIdentifiedItems(inventory, BLOOD_ALLOY_ID);
    }

    public boolean removeBlood(@NonNull Inventory inventory, int amount) {
        return removeIdentifiedItems(inventory, BLOOD_ID, amount);
    }

    public boolean removeBloodAlloy(@NonNull Inventory inventory, int amount) {
        return removeIdentifiedItems(inventory, BLOOD_ALLOY_ID, amount);
    }

    /**
     * Adds Blood and returns the amount that did not fit.
     */
    public int addBlood(@NonNull Inventory inventory, int amount) {
        return addIdentifiedItems(inventory, amount, this::createBlood);
    }

    /**
     * Adds Blood Alloy and returns the amount that did not fit.
     */
    public int addBloodAlloy(@NonNull Inventory inventory, int amount) {
        return addIdentifiedItems(inventory, amount, this::createBloodAlloy);
    }

    public @NonNull ItemStack createShopItem(@NonNull ShopProduct product) {
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

    public @NonNull Optional<String> internalItemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() < 1) {
            return Optional.empty();
        }
        return itemTags.readString(item, INTERNAL_ITEM_ID_KEY)
                .map(value -> value.toLowerCase(Locale.ROOT));
    }

    public @NonNull ItemStack prepareForMenuDisplay(@NonNull ItemStack item) {
        return prepareForMenuDisplay(item, List.of());
    }

    public @NonNull ItemStack createShopMenuDisplay(@NonNull ShopProduct product) {
        return prepareForMenuDisplay(
                createShopItem(product),
                product.menuLoreTemplates()
        );
    }

    public @NonNull ItemStack createEffectAxeMenuDisplay(
            @NonNull EffectAxeDefinition definition
    ) {
        return prepareForMenuDisplay(
                createEffectAxe(definition),
                EFFECT_AXE_MENU_LORE
        );
    }

    public @NonNull ItemStack createEffectAxeFuserDisplay(
            @NonNull EffectAxeDefinition definition,
            boolean selected
    ) {
        ItemStack display = prepareForMenuDisplay(
                createEffectAxe(definition),
                EFFECT_AXE_MENU_LORE
        );
        if (!selected) {
            display.removeEnchantment(Enchantment.DURABILITY);
        }
        return display;
    }

    public @NonNull ItemStack createCombinedEffectAxeMenuDisplay(
            @NonNull EffectAxeItemDefinition definition,
            int remainingUses
    ) {
        ItemStack result = createEffectAxe(definition);
        setRemainingUses(result, remainingUses);
        return prepareForMenuDisplay(
                result,
                EFFECT_AXE_MENU_LORE
        );
    }

    private ItemStack prepareForMenuDisplay(ItemStack item, List<String> menuLore) {
        requireUsableItem(item);
        ItemStack withoutItemId = itemTags.withString(
                item,
                INTERNAL_ITEM_ID_KEY,
                ""
        );
        ItemStack display = itemTags.withString(withoutItemId, OPERATION_ID_KEY, "");
        ItemMeta itemMeta = display.getItemMeta();
        if (!menuLore.isEmpty()) {
            List<Component> existingLore = itemMeta.hasLore()
                    ? itemMeta.lore()
                    : List.of();
            itemMeta.lore(mergeMenuLore(menuLore, existingLore));
        }
        itemMeta.addItemFlags(ItemFlag.values());
        display.setItemMeta(itemMeta);
        return display;
    }

    static @NonNull List<Component> mergeMenuLore(
            @NonNull List<String> menuLore,
            @NonNull List<Component> existingLore
    ) {
        List<Component> mergedLore = new ArrayList<>(
                BloodstoneText.deserializeLines(menuLore)
        );
        for (Component existingLine : existingLore) {
            if (!mergedLore.contains(existingLine)) {
                mergedLore.add(existingLine);
            }
        }
        return List.copyOf(mergedLore);
    }

    public @NonNull ItemStack withOperationId(@NonNull ItemStack item, @NonNull UUID operationId) {
        requireUsableItem(item);
        return itemTags.withString(item, OPERATION_ID_KEY, operationId.toString());
    }

    public @NonNull ItemStack withoutOperationId(@NonNull ItemStack item) {
        requireUsableItem(item);
        return itemTags.withString(item, OPERATION_ID_KEY, "");
    }

    public @NonNull Optional<UUID> operationId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() < 1) {
            return Optional.empty();
        }
        return itemTags.readString(item, OPERATION_ID_KEY).flatMap(value -> {
            try {
                return Optional.of(UUID.fromString(value));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    private ItemStack createIdentifiedItem(
            Material material,
            int amount,
            short data,
            String displayNameTemplate,
            String itemId
    ) {
        ItemStack item = new ItemStack(material, checkedStackAmount(material, amount), data);
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.displayName(BloodstoneText.deserialize(displayNameTemplate));
        item.setItemMeta(itemMeta);
        return itemTags.withString(item, INTERNAL_ITEM_ID_KEY, itemId);
    }

    private boolean hasInternalId(ItemStack item, String expectedItemId) {
        return internalItemId(item).filter(expectedItemId::equals).isPresent();
    }

    private int countIdentifiedItems(Inventory inventory, String itemId) {
        long count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (hasInternalId(item, itemId)) {
                count += item.getAmount();
            }
        }
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private boolean removeIdentifiedItems(Inventory inventory, String itemId, int amount) {
        requirePositiveAmount(amount);
        if (countIdentifiedItems(inventory, itemId) < amount) {
            return false;
        }

        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!hasInternalId(item, itemId)) {
                continue;
            }

            int removedFromStack = Math.min(remaining, item.getAmount());
            int amountAfterRemoval = item.getAmount() - removedFromStack;
            if (amountAfterRemoval == 0) {
                inventory.clear(slot);
            } else {
                item.setAmount(amountAfterRemoval);
                inventory.setItem(slot, item);
            }
            remaining -= removedFromStack;
        }
        if (remaining != 0) {
            throw new IllegalStateException("Inventory changed while removing Bloodstone currency");
        }
        return true;
    }

    private int addIdentifiedItems(Inventory inventory, int amount, CurrencyFactory factory) {
        requirePositiveAmount(amount);
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, 64);
            stacks.add(factory.create(stackAmount));
            remaining -= stackAmount;
        }

        HashMap<Integer, ItemStack> leftovers = inventory.addItem(stacks.toArray(ItemStack[]::new));
        long leftoverAmount = leftovers.values().stream().mapToLong(ItemStack::getAmount).sum();
        return leftoverAmount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) leftoverAmount;
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

    private void requireDamageable(ItemStack item) {
        requireUsableItem(item);
        if (item.getType().getMaxDurability() < 1) {
            throw new IllegalArgumentException(item.getType() + " does not have durability");
        }
    }

    public enum Classification {
        INCLUSIVE("classification.inclusive", "<gray>Inclusive</gray>"),
        EXCLUSIVE("classification.exclusive", "<gray>Exclusive</gray>"),
        SOULBOUND("classification.soulbound", "<gray>Soulbound</gray>");

        private final String internalId;
        private final Component lore;

        Classification(String internalId, String loreTemplate) {
            this.internalId = internalId;
            this.lore = BloodstoneText.deserialize(loreTemplate);
        }

        String internalId() {
            return internalId;
        }

        public Component lore() {
            return lore;
        }
    }

    public enum ShopProduct {
        SHARPNESS_IV_SWORD(2, List.of("<gray>Sharpness IV</gray>", "<gray>Knockback II</gray>", "<gray>Fire Aspect II</gray>")),
        SHARPNESS_V_SWORD(4, List.of("<gray>Sharpness V</gray>", "<gray>Knockback II</gray>", "<gray>Fire Aspect II</gray>")),
        POWER_V_BOW(4, List.of("<gray>Power V</gray>", "<gray>Punch II</gray>", "<gray>Flame I</gray>", "<gray>Infinity I</gray>", "<gray>Unbreaking III</gray>")),
        SHARPNESS_IV_AXE(2, List.of("<gray>Sharpness IV</gray>", "<gray>Knockback II</gray>", "<gray>Fire Aspect II</gray>")),
        SHARPNESS_V_AXE(4, List.of("<gray>Sharpness V</gray>", "<gray>Knockback II</gray>", "<gray>Fire Aspect II</gray>")),
        PROTECTION_IV_HELMET(1, List.of("<gray>Protection IV</gray>", "<gray>Unbreaking III</gray>")),
        PROTECTION_IV_CHESTPLATE(1, List.of("<gray>Protection IV</gray>", "<gray>Unbreaking III</gray>")),
        PROTECTION_IV_LEGGINGS(1, List.of("<gray>Protection IV</gray>", "<gray>Unbreaking III</gray>")),
        PROTECTION_IV_BOOTS(1, List.of("<gray>Protection IV</gray>", "<gray>Unbreaking III</gray>")),
        GOLDEN_APPLE(5, List.of()),
        STRENGTH_POTION(3, List.of("<gray>Strength I (03:00)</gray>")),
        RESISTANCE_POTION(2, List.of("<gray>Resistance (03:00)</gray>")),
        SPEED_POTION(2, List.of("<gray>Speed I (03:00)</gray>")),
        FIRE_RESISTANCE_POTION(1, List.of("<gray>Fire Resistance I (03:00)</gray>"));

        private final int bloodAlloyCost;
        private final List<String> menuLoreTemplates;

        ShopProduct(int bloodAlloyCost, List<String> menuLoreTemplates) {
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

    @FunctionalInterface
    private interface CurrencyFactory {
        ItemStack create(int amount);
    }

    static final class UnsafeItemTags {
        private static final Pattern SAFE_VALUE = Pattern.compile("[a-zA-Z0-9._:-]*");
        private static final int MAXIMUM_COLLECTION_SIZE = 1_048_576;
        private static final int MAXIMUM_DEPTH = 64;
        private static final int MAXIMUM_DECOMPRESSED_SIZE = 4_194_304;

        ItemStack withString(ItemStack item, String key, String value) {
            if (!SAFE_VALUE.matcher(value).matches()) {
                throw new IllegalArgumentException("Private item tag contains unsupported characters");
            }
            try {
                byte[] serializedItem = Bukkit.getUnsafe().serializeItem(item);
                ItemStack modifiedItem = Bukkit.getUnsafe().deserializeItem(
                        writeNbtString(serializedItem, key, value)
                );
                if (modifiedItem == null) {
                    throw new IllegalStateException(
                            "Carbon returned no item after writing private data"
                    );
                }
                return modifiedItem;
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not write private Bloodstone item data", exception);
            }
        }

        Optional<String> readString(ItemStack item, String key) {
            try {
                byte[] serializedItem = Bukkit.getUnsafe().serializeItem(item);
                Optional<String> value = readNbtString(serializedItem, key);
                return value.filter(candidate -> !candidate.isBlank());
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not read private Bloodstone item data", exception);
            }
        }

        byte[] writeNbtString(byte[] serializedItem, String key, String value) throws IOException {
            NbtCompression compression = compressionOf(serializedItem);
            ByteArrayOutputStream modifiedNbt = new ByteArrayOutputStream(serializedItem.length);
            try (DataInputStream input = new DataInputStream(new BoundedInputStream(
                    decompressedInput(serializedItem, compression),
                    MAXIMUM_DECOMPRESSED_SIZE
            )); DataOutputStream output = new DataOutputStream(modifiedNbt)) {
                int rootType = input.readUnsignedByte();
                if (rootType != 10) {
                    throw new IOException("Serialized item does not start with an NBT compound");
                }
                output.writeByte(rootType);
                output.writeUTF(input.readUTF());
                copyRootCompoundWithString(input, output, key, value, 0);
            }
            return compress(modifiedNbt.toByteArray(), compression);
        }

        Optional<String> readNbtString(byte[] serializedItem, String key) throws IOException {
            try (DataInputStream input = new DataInputStream(
                    new BoundedInputStream(
                            decompressedInput(serializedItem, compressionOf(serializedItem)),
                            MAXIMUM_DECOMPRESSED_SIZE
                    )
            )) {
                int rootType = input.readUnsignedByte();
                if (rootType != 10) {
                    throw new IOException("Serialized item does not start with an NBT compound");
                }
                input.readUTF();
                return readRootItemTagString(input, key, 0);
            }
        }

        private void copyRootCompoundWithString(
                DataInputStream input,
                DataOutputStream output,
                String key,
                String value,
                int depth
        ) throws IOException {
            checkDepth(depth);
            boolean itemTagFound = false;
            while (true) {
                int type = input.readUnsignedByte();
                if (type == 0) {
                    if (!itemTagFound && !value.isEmpty()) {
                        output.writeByte(10);
                        output.writeUTF("tag");
                        output.writeByte(8);
                        output.writeUTF(key);
                        output.writeUTF(value);
                        output.writeByte(0);
                    }
                    output.writeByte(0);
                    return;
                }

                String name = input.readUTF();
                if (name.equals("tag") && type != 10) {
                    throw new IOException("Serialized item tag is not an NBT compound");
                }
                if (name.equals("tag") && itemTagFound) {
                    throw new IOException("Serialized item contains duplicate tag compounds");
                }
                output.writeByte(type);
                output.writeUTF(name);
                if (name.equals("tag")) {
                    copyTargetCompoundWithString(input, output, key, value, depth + 1);
                    itemTagFound = true;
                } else {
                    copyPayload(input, output, type, depth + 1);
                }
            }
        }

        private Optional<String> readRootItemTagString(
                DataInputStream input,
                String key,
                int depth
        ) throws IOException {
            checkDepth(depth);
            boolean itemTagFound = false;
            Optional<String> value = Optional.empty();
            while (true) {
                int type = input.readUnsignedByte();
                if (type == 0) {
                    return value;
                }
                String name = input.readUTF();
                if (name.equals("tag")) {
                    if (type != 10) {
                        throw new IOException("Serialized item tag is not an NBT compound");
                    }
                    if (itemTagFound) {
                        throw new IOException("Serialized item contains duplicate tag compounds");
                    }
                    value = readDirectString(input, key, depth + 1);
                    itemTagFound = true;
                } else {
                    skipPayload(input, type, depth + 1);
                }
            }
        }

        private Optional<String> readDirectString(
                DataInputStream input,
                String key,
                int depth
        ) throws IOException {
            checkDepth(depth);
            Optional<String> value = Optional.empty();
            while (true) {
                int type = input.readUnsignedByte();
                if (type == 0) {
                    return value;
                }
                String name = input.readUTF();
                if (name.equals(key)) {
                    if (type != 8) {
                        throw new IOException("Private item data is not an NBT string");
                    }
                    if (value.isPresent()) {
                        throw new IOException("Serialized item contains duplicate private data");
                    }
                    value = Optional.of(input.readUTF());
                } else {
                    skipPayload(input, type, depth + 1);
                }
            }
        }

        private void copyTargetCompoundWithString(
                DataInputStream input,
                DataOutputStream output,
                String key,
                String value,
                int depth
        ) throws IOException {
            checkDepth(depth);
            boolean valueWritten = false;
            while (true) {
                int type = input.readUnsignedByte();
                if (type == 0) {
                    if (!valueWritten && !value.isEmpty()) {
                        output.writeByte(8);
                        output.writeUTF(key);
                        output.writeUTF(value);
                    }
                    output.writeByte(0);
                    return;
                }

                String name = input.readUTF();
                if (name.equals(key)) {
                    skipPayload(input, type, depth + 1);
                    if (!valueWritten && !value.isEmpty()) {
                        output.writeByte(8);
                        output.writeUTF(key);
                        output.writeUTF(value);
                        valueWritten = true;
                    }
                    continue;
                }

                output.writeByte(type);
                output.writeUTF(name);
                copyPayload(input, output, type, depth + 1);
            }
        }

        private void copyPayload(
                DataInputStream input,
                DataOutputStream output,
                int type,
                int depth
        ) throws IOException {
            checkDepth(depth);
            switch (type) {
                case 1 -> output.writeByte(input.readByte());
                case 2 -> output.writeShort(input.readShort());
                case 3 -> output.writeInt(input.readInt());
                case 4 -> output.writeLong(input.readLong());
                case 5 -> output.writeFloat(input.readFloat());
                case 6 -> output.writeDouble(input.readDouble());
                case 7 -> {
                    int size = checkedCollectionSize(input.readInt());
                    output.writeInt(size);
                    byte[] values = new byte[size];
                    input.readFully(values);
                    output.write(values);
                }
                case 8 -> output.writeUTF(input.readUTF());
                case 9 -> {
                    int elementType = input.readUnsignedByte();
                    int size = checkedCollectionSize(input.readInt());
                    output.writeByte(elementType);
                    output.writeInt(size);
                    for (int index = 0; index < size; index++) {
                        copyPayload(input, output, elementType, depth + 1);
                    }
                }
                case 10 -> copyCompound(input, output, depth + 1);
                case 11 -> {
                    int size = checkedCollectionSize(input.readInt());
                    output.writeInt(size);
                    for (int index = 0; index < size; index++) {
                        output.writeInt(input.readInt());
                    }
                }
                case 12 -> {
                    int size = checkedCollectionSize(input.readInt());
                    output.writeInt(size);
                    for (int index = 0; index < size; index++) {
                        output.writeLong(input.readLong());
                    }
                }
                default -> throw new IOException("Unsupported NBT tag type: " + type);
            }
        }

        private void copyCompound(
                DataInputStream input,
                DataOutputStream output,
                int depth
        ) throws IOException {
            checkDepth(depth);
            while (true) {
                int type = input.readUnsignedByte();
                output.writeByte(type);
                if (type == 0) {
                    return;
                }
                output.writeUTF(input.readUTF());
                copyPayload(input, output, type, depth + 1);
            }
        }

        private InputStream decompressedInput(
                byte[] serializedItem,
                NbtCompression compression
        ) throws IOException {
            ByteArrayInputStream bytes = new ByteArrayInputStream(serializedItem);
            return switch (compression) {
                case RAW -> bytes;
                case GZIP -> new GZIPInputStream(bytes);
                case ZLIB -> new InflaterInputStream(bytes);
            };
        }

        private byte[] compress(byte[] rawNbt, NbtCompression compression) throws IOException {
            if (compression == NbtCompression.RAW) {
                return rawNbt;
            }
            ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream(rawNbt.length);
            try (OutputStream output = switch (compression) {
                case RAW -> throw new IllegalStateException("Raw NBT does not require compression");
                case GZIP -> new GZIPOutputStream(compressedBytes);
                case ZLIB -> new DeflaterOutputStream(compressedBytes);
            }) {
                output.write(rawNbt);
            }
            return compressedBytes.toByteArray();
        }

        private NbtCompression compressionOf(byte[] serializedItem) {
            boolean gzip = serializedItem.length >= 2
                    && (serializedItem[0] & 0xff) == 0x1f
                    && (serializedItem[1] & 0xff) == 0x8b;
            if (gzip) {
                return NbtCompression.GZIP;
            }
            return hasZlibHeader(serializedItem) ? NbtCompression.ZLIB : NbtCompression.RAW;
        }

        private boolean hasZlibHeader(byte[] serializedItem) {
            if (serializedItem.length < 2) {
                return false;
            }
            int compressionMethodAndFlags = serializedItem[0] & 0xff;
            int additionalFlags = serializedItem[1] & 0xff;
            return (compressionMethodAndFlags & 0x0f) == 8
                    && (compressionMethodAndFlags >>> 4) <= 7
                    && ((compressionMethodAndFlags << 8) + additionalFlags) % 31 == 0;
        }

        private void skipPayload(DataInputStream input, int type, int depth)
                throws IOException {
            checkDepth(depth);
            switch (type) {
                case 1 -> input.readByte();
                case 2 -> input.readShort();
                case 3 -> input.readInt();
                case 4 -> input.readLong();
                case 5 -> input.readFloat();
                case 6 -> input.readDouble();
                case 7 -> input.skipNBytes(checkedCollectionSize(input.readInt()));
                case 8 -> input.readUTF();
                case 9 -> {
                    int elementType = input.readUnsignedByte();
                    int size = checkedCollectionSize(input.readInt());
                    for (int index = 0; index < size; index++) {
                        skipPayload(input, elementType, depth + 1);
                    }
                }
                case 10 -> skipCompound(input, depth + 1);
                case 11 -> input.skipNBytes(Math.multiplyExact(
                        checkedCollectionSize(input.readInt()),
                        Integer.BYTES
                ));
                case 12 -> input.skipNBytes(Math.multiplyExact(
                        checkedCollectionSize(input.readInt()),
                        Long.BYTES
                ));
                default -> throw new IOException("Unsupported NBT tag type: " + type);
            }
        }

        private void skipCompound(DataInputStream input, int depth) throws IOException {
            checkDepth(depth);
            while (true) {
                int type = input.readUnsignedByte();
                if (type == 0) {
                    return;
                }
                input.readUTF();
                skipPayload(input, type, depth + 1);
            }
        }

        private int checkedCollectionSize(int size) throws IOException {
            if (size < 0 || size > MAXIMUM_COLLECTION_SIZE) {
                throw new IOException("Serialized item contains an invalid NBT collection size");
            }
            return size;
        }

        private void checkDepth(int depth) throws IOException {
            if (depth > MAXIMUM_DEPTH) {
                throw new IOException("Serialized item NBT is nested too deeply");
            }
        }

        private enum NbtCompression {
            RAW,
            GZIP,
            ZLIB
        }

        private static final class BoundedInputStream extends InputStream {
            private final InputStream delegate;
            private final long maximumBytes;
            private long bytesRead;

            private BoundedInputStream(InputStream delegate, long maximumBytes) {
                this.delegate = delegate;
                this.maximumBytes = maximumBytes;
            }

            @Override
            public int read() throws IOException {
                ensureAvailable(1);
                int value = delegate.read();
                if (value >= 0) {
                    bytesRead++;
                }
                return value;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                ensureAvailable(length);
                int count = delegate.read(bytes, offset, length);
                if (count > 0) {
                    bytesRead += count;
                }
                return count;
            }

            @Override
            public long skip(long byteCount) throws IOException {
                ensureAvailable(byteCount);
                long skipped = delegate.skip(byteCount);
                bytesRead += skipped;
                return skipped;
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }

            private void ensureAvailable(long requestedBytes) throws IOException {
                if (requestedBytes < 0 || requestedBytes > maximumBytes - bytesRead) {
                    throw new IOException("Serialized item NBT exceeds the maximum supported size");
                }
            }
        }
    }
}
