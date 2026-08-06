package net.valoury.discord.bot.evidence;

import java.util.Optional;
import java.util.UUID;

public final class EvidenceButtonIdentifier {
    private EvidenceButtonIdentifier() {
        throw new UnsupportedOperationException("Evidence button identifiers cannot be instantiated");
    }

    public static String submit(UUID caseId) {
        return EvidenceBotConstants.SUBMIT_BUTTON_PREFIX + caseId;
    }

    public static String accept(UUID caseId) {
        return EvidenceBotConstants.ACCEPT_BUTTON_PREFIX + caseId;
    }

    public static String edit(UUID caseId) {
        return EvidenceBotConstants.EDIT_BUTTON_PREFIX + caseId;
    }

    public static String editChangeRequest(UUID caseId) {
        return EvidenceBotConstants.EDIT_CHANGES_BUTTON_PREFIX + caseId;
    }

    public static String requestChanges(UUID caseId) {
        return EvidenceBotConstants.CHANGES_BUTTON_PREFIX + caseId;
    }

    public static Optional<UUID> parseSubmit(String componentIdentifier) {
        return parse(componentIdentifier, EvidenceBotConstants.SUBMIT_BUTTON_PREFIX);
    }

    public static Optional<UUID> parseAccept(String componentIdentifier) {
        return parse(componentIdentifier, EvidenceBotConstants.ACCEPT_BUTTON_PREFIX);
    }

    public static Optional<UUID> parseEdit(String componentIdentifier) {
        return parse(componentIdentifier, EvidenceBotConstants.EDIT_BUTTON_PREFIX);
    }

    public static Optional<UUID> parseEditChangeRequest(String componentIdentifier) {
        return parse(componentIdentifier, EvidenceBotConstants.EDIT_CHANGES_BUTTON_PREFIX);
    }

    public static Optional<UUID> parseRequestChanges(String componentIdentifier) {
        return parse(componentIdentifier, EvidenceBotConstants.CHANGES_BUTTON_PREFIX);
    }

    private static Optional<UUID> parse(String componentIdentifier, String prefix) {
        if (componentIdentifier == null || !componentIdentifier.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(componentIdentifier.substring(prefix.length())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
