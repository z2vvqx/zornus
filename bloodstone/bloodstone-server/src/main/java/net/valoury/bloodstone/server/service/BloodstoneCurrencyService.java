package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.BloodstoneText;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

public final class BloodstoneCurrencyService {

    private static final String BLOOD_DISPLAY_NAME = "<white>Blood</white>";
    private static final String BLOOD_ALLOY_DISPLAY_NAME =
            "<white>Blood Alloy</white>";
    private static final String BLOOD_ID = "blood";
    private static final String BLOOD_ALLOY_ID = "blood_alloy";
    private static final int CURRENCY_STACK_SIZE = 64;

    private final BloodstoneItemIdentityService itemIdentity;

    public BloodstoneCurrencyService(
            BloodstoneItemIdentityService itemIdentity
    ) {
        this.itemIdentity = Objects.requireNonNull(
                itemIdentity,
                "Item identity cannot be null"
        );
    }

    public void validateRuntime() {
        ItemStack blood = createBlood(1);
        if (!isBlood(blood)
                || !blood.hasItemMeta()
                || !blood.getItemMeta().hasDisplayName()
                || !BloodstoneText.deserialize(BLOOD_DISPLAY_NAME)
                .equals(blood.getItemMeta().displayName())) {
            throw new IllegalStateException(
                    "Blood item identity or presentation is unavailable"
            );
        }
        ItemStack bloodAlloy = createBloodAlloy(1);
        if (!isBloodAlloy(bloodAlloy)) {
            throw new IllegalStateException(
                    "Blood Alloy item identity is unavailable"
            );
        }
    }

    public @NonNull ItemStack createBlood(int amount) {
        return createCurrency(
                Material.REDSTONE,
                amount,
                BLOOD_DISPLAY_NAME,
                BLOOD_ID
        );
    }

    public @NonNull ItemStack createBloodAlloy(int amount) {
        return createCurrency(
                Material.NETHER_BRICK_ITEM,
                amount,
                BLOOD_ALLOY_DISPLAY_NAME,
                BLOOD_ALLOY_ID
        );
    }

    public boolean isBlood(ItemStack item) {
        return itemIdentity.hasInternalItemId(item, BLOOD_ID);
    }

    public boolean isBloodAlloy(ItemStack item) {
        return itemIdentity.hasInternalItemId(item, BLOOD_ALLOY_ID);
    }

    public int countBlood(@NonNull Inventory inventory) {
        return count(inventory, BLOOD_ID);
    }

    public int countBloodAlloy(@NonNull Inventory inventory) {
        return count(inventory, BLOOD_ALLOY_ID);
    }

    public boolean removeBlood(@NonNull Inventory inventory, int amount) {
        return remove(inventory, BLOOD_ID, amount);
    }

    public boolean removeBloodAlloy(
            @NonNull Inventory inventory,
            int amount
    ) {
        return remove(inventory, BLOOD_ALLOY_ID, amount);
    }

    public int addBlood(@NonNull Inventory inventory, int amount) {
        return add(inventory, amount, this::createBlood);
    }

    public int addBloodAlloy(@NonNull Inventory inventory, int amount) {
        return add(inventory, amount, this::createBloodAlloy);
    }

    private ItemStack createCurrency(
            Material material,
            int amount,
            String displayNameTemplate,
            String itemId
    ) {
        requirePositiveAmount(amount);
        if (amount > material.getMaxStackSize()) {
            throw new IllegalArgumentException(
                    "Amount exceeds " + material
                            + " maximum stack size of "
                            + material.getMaxStackSize()
            );
        }
        ItemStack item = new ItemStack(material, amount);
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.displayName(BloodstoneText.deserialize(displayNameTemplate));
        item.setItemMeta(itemMeta);
        return itemIdentity.withInternalItemId(item, itemId);
    }

    private int count(Inventory inventory, String itemId) {
        Objects.requireNonNull(inventory, "Inventory cannot be null");
        long count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (itemIdentity.hasInternalItemId(item, itemId)) {
                count += item.getAmount();
            }
        }
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private boolean remove(
            Inventory inventory,
            String itemId,
            int amount
    ) {
        Objects.requireNonNull(inventory, "Inventory cannot be null");
        requirePositiveAmount(amount);
        if (count(inventory, itemId) < amount) {
            return false;
        }

        int remaining = amount;
        for (int slot = 0;
             slot < inventory.getSize() && remaining > 0;
             slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!itemIdentity.hasInternalItemId(item, itemId)) {
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
            throw new IllegalStateException(
                    "Inventory changed while removing Bloodstone currency"
            );
        }
        return true;
    }

    private int add(
            Inventory inventory,
            int amount,
            IntFunction<ItemStack> itemFactory
    ) {
        Objects.requireNonNull(inventory, "Inventory cannot be null");
        requirePositiveAmount(amount);
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, CURRENCY_STACK_SIZE);
            stacks.add(itemFactory.apply(stackAmount));
            remaining -= stackAmount;
        }

        Map<Integer, ItemStack> leftovers = inventory.addItem(
                stacks.toArray(ItemStack[]::new)
        );
        long leftoverAmount = leftovers.values().stream()
                .mapToLong(ItemStack::getAmount)
                .sum();
        return leftoverAmount > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) leftoverAmount;
    }

    private static void requirePositiveAmount(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
