package net.valoury.bloodstone.server.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BloodstoneItemIdentityService {

    private static final String INTERNAL_ITEM_ID_KEY =
            "valoury_bloodstone_item";
    private static final String OPERATION_ID_KEY =
            "valoury_bloodstone_operation";

    private final UnsafeItemTags itemTags;

    public BloodstoneItemIdentityService() {
        this(new UnsafeItemTags());
    }

    BloodstoneItemIdentityService(UnsafeItemTags itemTags) {
        this.itemTags = Objects.requireNonNull(
                itemTags,
                "Item tags cannot be null"
        );
    }

    public @NonNull ItemStack withInternalItemId(
            @NonNull ItemStack item,
            @NonNull String itemId
    ) {
        requireUsableItem(item);
        Objects.requireNonNull(itemId, "Item ID cannot be null");
        return itemTags.withString(item, INTERNAL_ITEM_ID_KEY, itemId);
    }

    public @NonNull ItemStack withoutInternalItemId(
            @NonNull ItemStack item
    ) {
        requireUsableItem(item);
        return itemTags.withString(item, INTERNAL_ITEM_ID_KEY, "");
    }

    public @NonNull Optional<String> internalItemId(
            @Nullable ItemStack item
    ) {
        if (!isUsableItem(item)) {
            return Optional.empty();
        }
        return itemTags.readString(item, INTERNAL_ITEM_ID_KEY)
                .map(value -> value.toLowerCase(Locale.ROOT));
    }

    public boolean hasInternalItemId(
            @Nullable ItemStack item,
            @NonNull String expectedItemId
    ) {
        Objects.requireNonNull(
                expectedItemId,
                "Expected item ID cannot be null"
        );
        return internalItemId(item).filter(expectedItemId::equals).isPresent();
    }

    public @NonNull ItemStack withOperationId(
            @NonNull ItemStack item,
            @NonNull UUID operationId
    ) {
        requireUsableItem(item);
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        return itemTags.withString(
                item,
                OPERATION_ID_KEY,
                operationId.toString()
        );
    }

    public @NonNull ItemStack withoutOperationId(
            @NonNull ItemStack item
    ) {
        requireUsableItem(item);
        return itemTags.withString(item, OPERATION_ID_KEY, "");
    }

    public @NonNull Optional<UUID> operationId(
            @Nullable ItemStack item
    ) {
        if (!isUsableItem(item)) {
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

    private static boolean isUsableItem(@Nullable ItemStack item) {
        return item != null
                && item.getType() != Material.AIR
                && item.getAmount() > 0;
    }

    private static void requireUsableItem(ItemStack item) {
        Objects.requireNonNull(item, "Item cannot be null");
        if (!isUsableItem(item)) {
            throw new IllegalArgumentException("Item must not be empty");
        }
    }
}
