package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.EffectAxeDefinitions.EffectAxeDefinition;
import net.valoury.bloodstone.server.EffectAxeItemDefinition;
import net.valoury.bloodstone.server.model.BloodstoneShopProduct;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class BloodstoneItemDisplayService {

    private final BloodstoneItemIdentityService itemIdentity;
    private final BloodstoneItemService itemService;
    private final BloodstoneEffectAxeService effectAxeService;

    public BloodstoneItemDisplayService(
            BloodstoneItemIdentityService itemIdentity,
            BloodstoneItemService itemService,
            BloodstoneEffectAxeService effectAxeService
    ) {
        this.itemIdentity = Objects.requireNonNull(
                itemIdentity,
                "Item identity cannot be null"
        );
        this.itemService = Objects.requireNonNull(
                itemService,
                "Item service cannot be null"
        );
        this.effectAxeService = Objects.requireNonNull(
                effectAxeService,
                "Effect Axe service cannot be null"
        );
    }

    public void validateRuntime() {
        EffectAxeDefinition definition =
                net.valoury.bloodstone.server.EffectAxeDefinitions.SPEED;
        ItemStack selectedDisplay = createEffectAxeFuserDisplay(
                definition,
                true
        );
        ItemStack unselectedDisplay = createEffectAxeFuserDisplay(
                definition,
                false
        );
        UUID operationId = UUID.randomUUID();
        ItemStack privateItem = itemIdentity.withOperationId(
                effectAxeService.create(definition),
                operationId
        );
        ItemStack sanitizedDisplay = prepareForMenuDisplay(privateItem);
        List<String> menuLore = effectAxeMenuLore(definition);
        Component sharpnessLore = BloodstoneText.deserialize(
                menuLore.getFirst()
        );
        Component unbreakingLore = BloodstoneText.deserialize(
                menuLore.getLast()
        );
        if (!effectAxeService.isEffectAxe(privateItem)
                || itemIdentity.operationId(privateItem)
                .filter(operationId::equals).isEmpty()
                || itemIdentity.internalItemId(sanitizedDisplay).isPresent()
                || itemIdentity.operationId(sanitizedDisplay).isPresent()
                || selectedDisplay.getEnchantmentLevel(Enchantment.DAMAGE_ALL)
                != BloodstoneEffectAxeService.sharpnessLevel(definition)
                || selectedDisplay.getEnchantmentLevel(Enchantment.DURABILITY)
                != BloodstoneEffectAxeService.unbreakingLevel()
                || unselectedDisplay.getEnchantmentLevel(
                Enchantment.DAMAGE_ALL) != 0
                || unselectedDisplay.getEnchantmentLevel(
                Enchantment.DURABILITY) != 0
                || !selectedDisplay.getItemMeta().lore()
                .contains(sharpnessLore)
                || !unselectedDisplay.getItemMeta().lore()
                .contains(sharpnessLore)
                || !selectedDisplay.getItemMeta().lore()
                .contains(unbreakingLore)
                || !unselectedDisplay.getItemMeta().lore()
                .contains(unbreakingLore)) {
            throw new IllegalStateException(
                    "Axe Fuser selection glint or Unbreaking lore is invalid"
            );
        }
    }

    public @NonNull ItemStack prepareForMenuDisplay(
            @NonNull ItemStack item
    ) {
        return prepareForMenuDisplay(item, List.of());
    }

    public @NonNull ItemStack createShopMenuDisplay(
            @NonNull BloodstoneShopProduct product
    ) {
        return prepareForMenuDisplay(
                itemService.createShopItem(product),
                product.menuLoreTemplates()
        );
    }

    public @NonNull ItemStack createEffectAxeMenuDisplay(
            @NonNull EffectAxeDefinition definition
    ) {
        return prepareForMenuDisplay(
                effectAxeService.create(definition),
                effectAxeMenuLore(definition)
        );
    }

    public @NonNull ItemStack createEffectAxeFuserDisplay(
            @NonNull EffectAxeDefinition definition,
            boolean selected
    ) {
        ItemStack display = prepareForMenuDisplay(
                effectAxeService.create(definition),
                effectAxeMenuLore(definition)
        );
        if (!selected) {
            display.removeEnchantment(Enchantment.DAMAGE_ALL);
            display.removeEnchantment(Enchantment.DURABILITY);
        }
        return display;
    }

    public @NonNull ItemStack createCombinedEffectAxeMenuDisplay(
            @NonNull EffectAxeItemDefinition definition,
            int remainingDurability
    ) {
        ItemStack result = effectAxeService.create(definition);
        effectAxeService.setRemainingDurability(
                result,
                remainingDurability
        );
        return prepareForMenuDisplay(
                result,
                effectAxeMenuLore(definition)
        );
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

    private static List<String> effectAxeMenuLore(
            EffectAxeItemDefinition definition
    ) {
        String sharpnessLevel = BloodstoneEffectAxeService.isFused(definition)
                ? "IV"
                : "III";
        return List.of(
                "<gray>Sharpness " + sharpnessLevel + "</gray>",
                "<gray>Unbreaking I</gray>"
        );
    }

    private ItemStack prepareForMenuDisplay(
            ItemStack item,
            List<String> menuLore
    ) {
        requireUsableItem(item);
        ItemStack withoutItemId = itemIdentity.withoutInternalItemId(item);
        ItemStack display = itemIdentity.withoutOperationId(withoutItemId);
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

    private static void requireUsableItem(ItemStack item) {
        Objects.requireNonNull(item, "Item cannot be null");
        if (item.getType() == Material.AIR || item.getAmount() < 1) {
            throw new IllegalArgumentException(
                    "Cannot display an empty item"
            );
        }
    }
}
