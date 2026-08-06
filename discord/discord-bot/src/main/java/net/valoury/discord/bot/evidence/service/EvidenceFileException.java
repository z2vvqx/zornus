package net.valoury.discord.bot.evidence.service;

public final class EvidenceFileException extends RuntimeException {
    private final Reason reason;

    public EvidenceFileException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_TYPE,
        TOO_LARGE,
        TRANSFER_FAILED
    }
}
