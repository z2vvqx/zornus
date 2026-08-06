package net.valoury.discord.bot.evidence.message;

import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.file.Path;

public record PreparedEvidenceFile(
        Path path,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256
) {
    public FileUpload createUpload() {
        return FileUpload.fromData(path, fileName);
    }
}
