package net.valoury.bloodstone.server.service;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

final class UnsafeItemTags {
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
