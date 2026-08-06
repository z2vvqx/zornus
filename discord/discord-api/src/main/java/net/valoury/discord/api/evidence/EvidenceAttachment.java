package net.valoury.discord.api.evidence;

public record EvidenceAttachment(
        long attachmentId,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256
) {
    public EvidenceAttachment {
        if (attachmentId <= 0 || sizeBytes <= 0) {
            throw new IllegalArgumentException("Evidence attachment identifiers and sizes must be positive");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Evidence attachment filename cannot be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Evidence attachment content type cannot be blank");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Evidence attachment SHA-256 digest is invalid");
        }
    }
}
