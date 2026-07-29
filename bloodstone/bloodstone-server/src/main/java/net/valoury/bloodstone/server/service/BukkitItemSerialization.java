package net.valoury.bloodstone.server.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

public final class BukkitItemSerialization {

    private static final int MAXIMUM_INVENTORY_SIZE = 1_000;
    private static final int MAXIMUM_ITEM_SIZE = 1_048_576;

    private BukkitItemSerialization() {
    }

    public static byte @NonNull [] serializeItem(@NonNull ItemStack item) throws IOException {
        if (item.getType() == Material.AIR || item.getAmount() < 1) {
            throw new IOException("Cannot serialize an empty Bukkit ItemStack");
        }
        try {
            return Bukkit.getUnsafe().serializeItem(item);
        } catch (RuntimeException exception) {
            throw new IOException("Could not serialize Bukkit ItemStack", exception);
        }
    }

    public static @NonNull ItemStack deserializeItem(byte @NonNull [] serializedItem)
            throws IOException {
        if (serializedItem.length == 0
                || serializedItem.length > MAXIMUM_ITEM_SIZE) {
            throw new IOException("Serialized Bukkit ItemStack size is invalid");
        }
        try {
            ItemStack item = Bukkit.getUnsafe().deserializeItem(serializedItem);
            if (item == null || item.getType() == Material.AIR || item.getAmount() < 1) {
                throw new IOException("Serialized value is not a usable Bukkit ItemStack");
            }
            return item;
        } catch (RuntimeException exception) {
            throw new IOException("Could not deserialize Bukkit ItemStack", exception);
        }
    }

    public static byte @NonNull [] serializeInventory(@NonNull Inventory inventory) throws IOException {
        return serializeContents(inventory.getContents());
    }

    public static byte @NonNull [] serializeContents(ItemStack @NonNull [] contents) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(contents.length);
            for (ItemStack item : contents) {
                boolean populated = item != null
                        && item.getType() != Material.AIR
                        && item.getAmount() > 0;
                output.writeBoolean(populated);
                if (populated) {
                    byte[] serializedItem = serializeItem(item);
                    output.writeInt(serializedItem.length);
                    output.write(serializedItem);
                }
            }
            return bytes.toByteArray();
        }
    }

    public static ItemStack @NonNull [] deserializeContents(byte @NonNull [] serializedContents)
            throws IOException {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(serializedContents);
             DataInputStream input = new DataInputStream(bytes)) {
            int inventorySize = input.readInt();
            if (inventorySize < 0
                    || inventorySize > MAXIMUM_INVENTORY_SIZE) {
                throw new IOException("Serialized inventory size is invalid: " + inventorySize);
            }

            ItemStack[] contents = new ItemStack[inventorySize];
            for (int slot = 0; slot < inventorySize; slot++) {
                if (!input.readBoolean()) {
                    continue;
                }
                int itemSize = input.readInt();
                if (itemSize < 1
                        || itemSize > MAXIMUM_ITEM_SIZE
                        || itemSize > bytes.available()) {
                    throw new IOException("Serialized inventory slot " + slot + " has an invalid size");
                }
                contents[slot] = deserializeItem(input.readNBytes(itemSize));
            }
            if (input.read() != -1) {
                throw new IOException("Serialized inventory contains trailing data");
            }
            return contents;
        } catch (EOFException exception) {
            throw new IOException("Serialized inventory ended unexpectedly", exception);
        }
    }
}
