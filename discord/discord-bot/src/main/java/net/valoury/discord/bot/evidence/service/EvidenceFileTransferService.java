package net.valoury.discord.bot.evidence.service;

import net.dv8tion.jda.api.entities.Message;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import net.valoury.discord.bot.evidence.message.PreparedEvidenceFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EvidenceFileTransferService implements AutoCloseable {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "webm", "mov");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "log");
    private static final int COPY_BUFFER_SIZE = 16 * 1024;
    private static final int DETECTION_SAMPLE_SIZE = 4096;

    private final ExecutorService transferExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CompletableFuture<PreparedEvidenceFiles> prepare(
            List<Message.Attachment> attachments,
            long maximumFileSize
    ) {
        List<Message.Attachment> immutableAttachments = List.copyOf(attachments);
        if (immutableAttachments.size() > EvidenceBotConstants.MAXIMUM_ATTACHMENTS) {
            return CompletableFuture.failedFuture(new EvidenceFileException(
                    EvidenceFileException.Reason.TOO_LARGE,
                    "Too many evidence attachments"
            ));
        }
        long totalSize = immutableAttachments.stream().mapToLong(Message.Attachment::getSize).sum();
        if (totalSize > EvidenceBotConstants.MAXIMUM_TOTAL_UPLOAD_BYTES
                || immutableAttachments.stream().anyMatch(attachment -> attachment.getSize() > maximumFileSize)) {
            return CompletableFuture.failedFuture(new EvidenceFileException(
                    EvidenceFileException.Reason.TOO_LARGE,
                    "Evidence attachments exceed the configured upload limit"
            ));
        }
        if (immutableAttachments.isEmpty()) {
            return CompletableFuture.completedFuture(new PreparedEvidenceFiles(null, List.of()));
        }

        Path temporaryDirectory;
        try {
            temporaryDirectory = Files.createTempDirectory("valoury-evidence-");
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(new EvidenceFileException(
                    EvidenceFileException.Reason.TRANSFER_FAILED,
                    "Failed to create temporary evidence storage"
            ));
        }

        CompletableFuture<List<PreparedEvidenceFile>> preparation = CompletableFuture.completedFuture(
                new ArrayList<>());
        for (int index = 0; index < immutableAttachments.size(); index++) {
            int attachmentIndex = index;
            Message.Attachment attachment = immutableAttachments.get(index);
            preparation = preparation.thenCompose(preparedFiles -> prepareFile(
                            attachment,
                            attachmentIndex,
                            temporaryDirectory,
                            maximumFileSize
                    )
                    .thenApply(preparedFile -> {
                        preparedFiles.add(preparedFile);
                        return preparedFiles;
                    }));
        }
        return preparation.thenApply(files -> new PreparedEvidenceFiles(temporaryDirectory, files))
                .whenComplete((ignored, exception) -> {
                    if (exception != null) {
                        new PreparedEvidenceFiles(temporaryDirectory, List.of()).close();
                    }
                });
    }

    private CompletableFuture<PreparedEvidenceFile> prepareFile(
            Message.Attachment attachment,
            int index,
            Path temporaryDirectory,
            long maximumFileSize
    ) {
        if (attachment.getSize() <= 0 || attachment.getSize() > maximumFileSize) {
            return CompletableFuture.failedFuture(new EvidenceFileException(
                    EvidenceFileException.Reason.TOO_LARGE,
                    "Evidence attachment has an invalid size"
            ));
        }
        String safeFileName = "%02d-%s".formatted(index + 1, sanitizeFileName(attachment.getFileName()));
        Path targetPath = temporaryDirectory.resolve(safeFileName);
        return attachment.getProxy().download().thenApplyAsync(inputStream -> {
            try (InputStream source = inputStream) {
                String sha256 = copyAndHash(source, targetPath, maximumFileSize);
                long actualSize = Files.size(targetPath);
                String contentType = detectContentType(targetPath, safeFileName);
                return new PreparedEvidenceFile(targetPath, safeFileName, contentType, actualSize, sha256);
            } catch (EvidenceFileException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new EvidenceFileException(
                        EvidenceFileException.Reason.TRANSFER_FAILED,
                        "Failed to transfer evidence attachment"
                );
            }
        }, transferExecutor);
    }

    private static String copyAndHash(InputStream source, Path targetPath, long maximumFileSize) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        long copiedBytes = 0;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (OutputStream destination = Files.newOutputStream(targetPath)) {
            int readBytes;
            while ((readBytes = source.read(buffer)) != -1) {
                copiedBytes += readBytes;
                if (copiedBytes > maximumFileSize
                        || copiedBytes > EvidenceBotConstants.MAXIMUM_TOTAL_UPLOAD_BYTES) {
                    throw new EvidenceFileException(
                            EvidenceFileException.Reason.TOO_LARGE,
                            "Evidence attachment exceeded the upload limit during transfer"
                    );
                }
                digest.update(buffer, 0, readBytes);
                destination.write(buffer, 0, readBytes);
            }
        }
        if (copiedBytes == 0) {
            throw new EvidenceFileException(
                    EvidenceFileException.Reason.INVALID_TYPE,
                    "Empty evidence attachments are not accepted"
            );
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String detectContentType(Path path, String fileName) throws IOException {
        byte[] sample;
        try (InputStream inputStream = Files.newInputStream(path)) {
            sample = inputStream.readNBytes(DETECTION_SAMPLE_SIZE);
        }
        String extension = extension(fileName);
        if (isPng(sample) && extension.equals("png")) {
            return "image/png";
        }
        if (isJpeg(sample) && Set.of("jpg", "jpeg").contains(extension)) {
            return "image/jpeg";
        }
        if (isGif(sample) && extension.equals("gif")) {
            return "image/gif";
        }
        if (isWebp(sample) && extension.equals("webp")) {
            return "image/webp";
        }
        if (isIsoBaseMedia(sample) && Set.of("mp4", "mov").contains(extension)) {
            return extension.equals("mov") ? "video/quicktime" : "video/mp4";
        }
        if (isWebm(sample) && extension.equals("webm")) {
            return "video/webm";
        }
        if (isPlainText(sample) && TEXT_EXTENSIONS.contains(extension)) {
            return "text/plain";
        }
        throw new EvidenceFileException(
                EvidenceFileException.Reason.INVALID_TYPE,
                "Evidence attachment type does not match an allowed file format"
        );
    }

    private static String sanitizeFileName(String originalFileName) {
        String normalized = originalFileName == null ? "evidence" : originalFileName
                .replace('\\', '_')
                .replace('/', '_')
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (normalized.isBlank() || normalized.equals(".") || normalized.equals("..")) {
            normalized = "evidence";
        }
        return normalized.length() <= 90 ? normalized : normalized.substring(normalized.length() - 90);
    }

    private static String extension(String fileName) {
        int separatorIndex = fileName.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)
                && !VIDEO_EXTENSIONS.contains(extension)
                && !TEXT_EXTENSIONS.contains(extension)) {
            return "";
        }
        return extension;
    }

    private static boolean isPng(byte[] data) {
        return startsWith(data, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    }

    private static boolean isJpeg(byte[] data) {
        return startsWith(data, new int[]{0xFF, 0xD8, 0xFF});
    }

    private static boolean isGif(byte[] data) {
        return startsWithText(data, "GIF87a") || startsWithText(data, "GIF89a");
    }

    private static boolean isWebp(byte[] data) {
        return data.length >= 12
                && new String(data, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
    }

    private static boolean isIsoBaseMedia(byte[] data) {
        return data.length >= 12 && new String(data, 4, 4, StandardCharsets.US_ASCII).equals("ftyp");
    }

    private static boolean isWebm(byte[] data) {
        return startsWith(data, new int[]{0x1A, 0x45, 0xDF, 0xA3});
    }

    private static boolean isPlainText(byte[] data) {
        if (data.length == 0) {
            return false;
        }
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer decodedCharacters = CharBuffer.allocate(data.length);
        if (decoder.decode(ByteBuffer.wrap(data), decodedCharacters, false).isError()) {
            return false;
        }
        decodedCharacters.flip();
        while (decodedCharacters.hasRemaining()) {
            char character = decodedCharacters.get();
            if (character == 0
                    || (character < 0x09)
                    || (character > 0x0D && character < 0x20)) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithText(byte[] data, String prefix) {
        return startsWith(data, prefix.chars().toArray());
    }

    private static boolean startsWith(byte[] data, int[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((data[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        transferExecutor.shutdownNow();
    }
}
