package net.valoury.discord.bot.evidence.service;

import net.valoury.discord.bot.evidence.message.PreparedEvidenceFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class PreparedEvidenceFiles implements AutoCloseable {
    private final Path temporaryDirectory;
    private final List<PreparedEvidenceFile> files;

    PreparedEvidenceFiles(Path temporaryDirectory, List<PreparedEvidenceFile> files) {
        this.temporaryDirectory = temporaryDirectory;
        this.files = List.copyOf(files);
    }

    public List<PreparedEvidenceFile> files() {
        return files;
    }

    @Override
    public void close() {
        if (temporaryDirectory == null || !Files.exists(temporaryDirectory)) {
            return;
        }
        try (var paths = Files.walk(temporaryDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup; the operating system temp directory remains the recovery boundary.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup; submission state is handled independently.
        }
    }
}
