package net.valoury.discord.api.evidence;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvidenceSubmission(
        long submissionId,
        UUID caseId,
        long submittedByDiscordUserId,
        String incidentDescription,
        String proofDescription,
        String additionalContext,
        @Nullable String externalLink,
        long evidenceMessageId,
        Instant submittedAt,
        List<EvidenceAttachment> attachments
) {
    public EvidenceSubmission {
        attachments = List.copyOf(attachments);
    }
}
