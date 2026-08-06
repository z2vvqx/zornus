package net.valoury.discord.bot.evidence.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceFileTransferServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsAllowedFormatsFromContentsAndExtension() throws Exception {
        Path png = temporaryDirectory.resolve("proof.png");
        Files.write(png, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        Path log = temporaryDirectory.resolve("chat.log");
        Files.writeString(log, "player: message\n", StandardCharsets.UTF_8);

        assertEquals("image/png", EvidenceFileTransferService.detectContentType(png, "proof.png"));
        assertEquals("text/plain", EvidenceFileTransferService.detectContentType(log, "chat.log"));
    }

    @Test
    void rejectsExtensionSpoofingAndExecutableFiles() throws Exception {
        Path spoofedImage = temporaryDirectory.resolve("proof.png");
        Files.writeString(spoofedImage, "not an image", StandardCharsets.UTF_8);
        Path executable = temporaryDirectory.resolve("proof.exe");
        Files.write(executable, new byte[]{0x4D, 0x5A, 0x00});
        Path invalidText = temporaryDirectory.resolve("proof.log");
        Files.write(invalidText, new byte[]{(byte) 0xC3, 0x28});

        assertThrows(EvidenceFileException.class, () ->
                EvidenceFileTransferService.detectContentType(spoofedImage, "proof.png"));
        assertThrows(EvidenceFileException.class, () ->
                EvidenceFileTransferService.detectContentType(executable, "proof.exe"));
        assertThrows(EvidenceFileException.class, () ->
                EvidenceFileTransferService.detectContentType(invalidText, "proof.log"));
    }
}
