package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class UnsafeItemTagsTest {
    private static final String ITEM_ID_KEY = "valoury_bloodstone_item";

    private final BloodstoneItemService.UnsafeItemTags itemTags =
            new BloodstoneItemService.UnsafeItemTags();

    @Test
    void readsRawGzipAndCarbonZlibNbt() throws IOException {
        byte[] rawNbt = structuredItemNbt();

        assertEquals(Optional.of("blood"), itemTags.readNbtString(rawNbt, ITEM_ID_KEY));
        assertEquals(Optional.of("blood"),
                itemTags.readNbtString(compress(rawNbt, GZIPOutputStream::new), ITEM_ID_KEY));
        assertEquals(Optional.of("blood"),
                itemTags.readNbtString(compress(rawNbt, DeflaterOutputStream::new), ITEM_ID_KEY));
    }

    @Test
    void returnsEmptyWhenPrivateTagIsAbsent() throws IOException {
        assertEquals(Optional.empty(), itemTags.readNbtString(emptyNbt(), ITEM_ID_KEY));
    }

    @Test
    void rejectsOversizedNbtCollections() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(7);
            output.writeUTF("oversized");
            output.writeInt(1_048_577);
        }

        assertThrows(IOException.class,
                () -> itemTags.readNbtString(bytes.toByteArray(), ITEM_ID_KEY));
    }

    @Test
    void replacesOnlyTheRequestedPrivateTagInCarbonZlibNbt() throws IOException {
        byte[] serializedItem = compress(structuredItemNbt(), DeflaterOutputStream::new);

        byte[] withOperation = itemTags.writeNbtString(
                serializedItem,
                "valoury_bloodstone_operation",
                "0c8e7cd3-2a54-43bb-9c65-33b3ce1b82dc"
        );

        assertEquals(0x78, withOperation[0] & 0xff);
        assertEquals(Optional.of("blood"),
                itemTags.readNbtString(withOperation, ITEM_ID_KEY));
        assertEquals(Optional.of("0c8e7cd3-2a54-43bb-9c65-33b3ce1b82dc"),
                itemTags.readNbtString(withOperation, "valoury_bloodstone_operation"));
        assertEquals(Optional.of("preserved"),
                itemTags.readNbtString(withOperation, "unrelated"));

        byte[] withoutItemId = itemTags.writeNbtString(
                withOperation,
                ITEM_ID_KEY,
                ""
        );
        byte[] withoutPrivateTags = itemTags.writeNbtString(
                withoutItemId,
                "valoury_bloodstone_operation",
                ""
        );
        assertEquals(Optional.empty(),
                itemTags.readNbtString(withoutPrivateTags, ITEM_ID_KEY));
        assertEquals(Optional.empty(),
                itemTags.readNbtString(
                        withoutPrivateTags,
                        "valoury_bloodstone_operation"
                ));
        assertEquals(Optional.of("0c8e7cd3-2a54-43bb-9c65-33b3ce1b82dc"),
                itemTags.readNbtString(withoutItemId, "valoury_bloodstone_operation"));
        assertEquals(Optional.of("preserved"),
                itemTags.readNbtString(withoutPrivateTags, "unrelated"));
    }

    @Test
    void createsAnItemTagCompoundWhenTheSerializedItemHasNone() throws IOException {
        byte[] modified = itemTags.writeNbtString(emptyNbt(), ITEM_ID_KEY, "blood");

        assertEquals(Optional.of("blood"), itemTags.readNbtString(modified, ITEM_ID_KEY));
    }

    @Test
    void ignoresPrivateKeyNamesOutsideTheDirectItemTag() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(10);
            output.writeUTF("tag");
            output.writeByte(10);
            output.writeUTF("nested");
            output.writeByte(8);
            output.writeUTF(ITEM_ID_KEY);
            output.writeUTF("forged");
            output.writeByte(0);
            output.writeByte(0);
            output.writeByte(0);
        }

        assertEquals(Optional.empty(),
                itemTags.readNbtString(bytes.toByteArray(), ITEM_ID_KEY));
    }

    private byte[] emptyNbt() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private byte[] structuredItemNbt() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(8);
            output.writeUTF("id");
            output.writeUTF("minecraft:redstone");
            output.writeByte(10);
            output.writeUTF("tag");
            output.writeByte(8);
            output.writeUTF(ITEM_ID_KEY);
            output.writeUTF("blood");
            output.writeByte(8);
            output.writeUTF("unrelated");
            output.writeUTF("preserved");
            output.writeByte(0);
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private byte[] compress(byte[] rawNbt, CompressionStreamFactory factory) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream output = factory.create(bytes)) {
            output.write(rawNbt);
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface CompressionStreamFactory {
        OutputStream create(OutputStream output) throws IOException;
    }
}
