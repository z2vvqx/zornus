package net.valoury.discord.bot.evidence;

import java.util.Optional;
import java.util.UUID;

public final class EvidenceModalIdentifier {
    private EvidenceModalIdentifier() {
        throw new UnsupportedOperationException("Evidence modal identifiers cannot be instantiated");
    }

    public static String submission(UUID caseId) {
        return EvidenceBotConstants.SUBMISSION_MODAL_PREFIX + caseId;
    }

    public static String requestChanges(UUID caseId) {
        return EvidenceBotConstants.CHANGES_MODAL_PREFIX + caseId;
    }

    public static String edit(UUID caseId) {
        return EvidenceBotConstants.EDIT_MODAL_PREFIX + caseId;
    }

    public static String editChangeRequest(UUID caseId, long messageId) {
        return EvidenceBotConstants.EDIT_CHANGES_MODAL_PREFIX + caseId + ":" + messageId;
    }

    public static Optional<UUID> parseSubmission(String modalIdentifier) {
        return parse(modalIdentifier, EvidenceBotConstants.SUBMISSION_MODAL_PREFIX);
    }

    public static Optional<UUID> parseRequestChanges(String modalIdentifier) {
        return parse(modalIdentifier, EvidenceBotConstants.CHANGES_MODAL_PREFIX);
    }

    public static Optional<UUID> parseEdit(String modalIdentifier) {
        return parse(modalIdentifier, EvidenceBotConstants.EDIT_MODAL_PREFIX);
    }

    public static Optional<ChangeRequestEditTarget> parseEditChangeRequest(String modalIdentifier) {
        if (modalIdentifier == null || !modalIdentifier.startsWith(EvidenceBotConstants.EDIT_CHANGES_MODAL_PREFIX)) {
            return Optional.empty();
        }
        String identifier = modalIdentifier.substring(EvidenceBotConstants.EDIT_CHANGES_MODAL_PREFIX.length());
        int messageSeparator = identifier.lastIndexOf(':');
        if (messageSeparator < 0) {
            return Optional.empty();
        }
        try {
            UUID caseId = UUID.fromString(identifier.substring(0, messageSeparator));
            long messageId = Long.parseLong(identifier.substring(messageSeparator + 1));
            return messageId > 0
                    ? Optional.of(new ChangeRequestEditTarget(caseId, messageId))
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> parse(String modalIdentifier, String prefix) {
        if (modalIdentifier == null || !modalIdentifier.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(modalIdentifier.substring(prefix.length())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record ChangeRequestEditTarget(UUID caseId, long messageId) {
    }
}
