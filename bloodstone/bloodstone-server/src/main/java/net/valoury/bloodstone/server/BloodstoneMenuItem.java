package net.valoury.bloodstone.server;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Immutable presentation for a menu item. Bukkit item stacks remain mutable, so
 * each call to {@link #create(TagResolver...)} returns a fresh stack.
 */
public record BloodstoneMenuItem(
        @NonNull Material material,
        @NonNull String nameTemplate,
        @NonNull List<String> loreTemplates
) {

    public BloodstoneMenuItem {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(nameTemplate, "nameTemplate");
        loreTemplates = List.copyOf(loreTemplates);
    }

    public BloodstoneMenuItem(@NonNull Material material, @NonNull String nameTemplate) {
        this(material, nameTemplate, List.of());
    }

    public @NonNull ItemStack create(TagResolver... resolvers) {
        ItemStack item = new ItemStack(material);
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            throw new IllegalStateException(material + " does not support item metadata");
        }
        itemMeta.setDisplayName(BloodstoneText.legacy(nameTemplate, resolvers));
        if (!loreTemplates.isEmpty()) {
            itemMeta.setLore(BloodstoneText.legacyLines(loreTemplates, resolvers));
        }
        itemMeta.addItemFlags(ItemFlag.values());
        item.setItemMeta(itemMeta);
        return item;
    }
}
