package net.valoury.discord.api.evidence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EvidenceStorage extends AutoCloseable {
    CompletableFuture<EvidenceCase> createCase(EvidenceCaseRequest request);

    CompletableFuture<Optional<EvidenceCase>> findCaseByIdentifier(String punishmentIdentifier);

    CompletableFuture<Optional<EvidenceCase>> findCaseByThreadId(long threadId);

    CompletableFuture<Optional<EvidenceSubmission>> findLatestSubmission(UUID caseId);

    CompletableFuture<List<EvidenceCase>> claimPendingCases(
            Instant claimedAt,
            Duration staleClaimAge,
            int maximumCases
    );

    CompletableFuture<Optional<EvidenceCase>> activateCase(
            UUID caseId,
            long guildId,
            long forumChannelId,
            long threadId,
            long starterMessageId
    );

    CompletableFuture<Void> releaseCaseProvisioning(UUID caseId);

    CompletableFuture<Optional<EvidenceSettings>> findSettings();

    CompletableFuture<Void> saveSettings(EvidenceSettings settings);

    CompletableFuture<Boolean> beginSubmission(
            UUID caseId,
            long submissionId,
            long submittingDiscordUserId,
            boolean reviewerOverride,
            Instant startedAt,
            Duration staleUploadAge
    );

    CompletableFuture<Boolean> beginSubmissionEdit(
            UUID caseId,
            long submissionId,
            long submittingDiscordUserId,
            boolean reviewerOverride,
            Instant startedAt,
            Duration staleUploadAge
    );

    CompletableFuture<Optional<EvidenceCase>> completeSubmission(EvidenceSubmission submission);

    CompletableFuture<Void> failSubmission(UUID caseId, long submissionId);

    CompletableFuture<Optional<EvidenceCase>> reviewCase(
            UUID caseId,
            long reviewerDiscordUserId,
            EvidenceReviewDecision decision,
            String reviewReason,
            Instant reviewedAt
    );

    CompletableFuture<Boolean> recordActiveChangeRequestMessage(UUID caseId, long messageId);

    CompletableFuture<OptionalLong> findActiveChangeRequestMessageId(UUID caseId);

    CompletableFuture<Optional<EvidenceCase>> editChangeRequest(
            UUID caseId,
            long changeRequestMessageId,
            long reviewerDiscordUserId,
            String reviewReason,
            Instant editedAt
    );

    @Override
    void close();
}
