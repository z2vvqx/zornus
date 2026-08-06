package net.valoury.discord.api.evidence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class EvidenceService {
    private static final Pattern PUNISHMENT_IDENTIFIER_PATTERN = Pattern.compile("[A-Z0-9]{4}");
    private static final Pattern MINECRAFT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final EvidenceStorage storage;

    public EvidenceService(EvidenceStorage storage) {
        this.storage = Objects.requireNonNull(storage, "Evidence storage cannot be null");
    }

    public CompletableFuture<EvidenceCase> createCase(EvidenceCaseRequest request) {
        Objects.requireNonNull(request, "Evidence case request cannot be null");
        String normalizedIdentifier = normalizeIdentifier(request.punishmentIdentifier());
        requireMinecraftName(request.punishedPlayerName());
        Objects.requireNonNull(request.punishedPlayerId(), "Punished player identifier cannot be null");
        requireText(request.presetName(), "Evidence preset name cannot be blank");
        requireText(request.punishmentType(), "Evidence punishment type cannot be blank");
        requireText(request.reason(), "Evidence punishment reason cannot be blank");
        Objects.requireNonNull(request.punishmentCreatedAt(), "Punishment creation time cannot be null");
        if (request.presetApplicationNumber() < 1) {
            throw new IllegalArgumentException("Evidence preset application number must be positive");
        }
        if (request.issuingDiscordUserId() != null && request.issuingDiscordUserId() <= 0) {
            throw new IllegalArgumentException("Issuing Discord user identifier must be positive");
        }
        EvidenceCaseRequest normalizedRequest = new EvidenceCaseRequest(
                normalizedIdentifier,
                request.punishedPlayerId(),
                request.punishedPlayerName(),
                request.issuingPlayerId(),
                request.issuingDiscordUserId(),
                request.presetName().toLowerCase(Locale.ROOT),
                request.presetApplicationNumber(),
                request.punishmentType().toUpperCase(Locale.ROOT),
                request.reason(),
                request.punishmentCreatedAt(),
                request.punishmentExpiresAt()
        );
        return storage.createCase(normalizedRequest);
    }

    public CompletableFuture<Optional<EvidenceCase>> findCaseByIdentifier(String punishmentIdentifier) {
        return storage.findCaseByIdentifier(normalizeIdentifier(punishmentIdentifier));
    }

    public CompletableFuture<Optional<EvidenceCase>> findCaseByThreadId(long threadId) {
        requirePositive(threadId, "Evidence thread identifier must be positive");
        return storage.findCaseByThreadId(threadId);
    }

    public CompletableFuture<Optional<EvidenceSubmission>> findLatestSubmission(UUID caseId) {
        return storage.findLatestSubmission(
                Objects.requireNonNull(caseId, "Evidence case identifier cannot be null"));
    }

    public CompletableFuture<List<EvidenceCase>> claimPendingCases(
            Instant claimedAt,
            Duration staleClaimAge,
            int maximumCases
    ) {
        Objects.requireNonNull(claimedAt, "Evidence claim time cannot be null");
        requirePositiveDuration(staleClaimAge, "Evidence stale claim age must be positive");
        if (maximumCases < 1) {
            throw new IllegalArgumentException("Evidence claim size must be positive");
        }
        return storage.claimPendingCases(claimedAt, staleClaimAge, maximumCases);
    }

    public CompletableFuture<Optional<EvidenceCase>> activateCase(
            UUID caseId,
            long guildId,
            long forumChannelId,
            long threadId,
            long starterMessageId
    ) {
        Objects.requireNonNull(caseId, "Evidence case identifier cannot be null");
        requirePositive(guildId, "Evidence guild identifier must be positive");
        requirePositive(forumChannelId, "Evidence forum identifier must be positive");
        requirePositive(threadId, "Evidence thread identifier must be positive");
        requirePositive(starterMessageId, "Evidence starter message identifier must be positive");
        return storage.activateCase(caseId, guildId, forumChannelId, threadId, starterMessageId);
    }

    public CompletableFuture<Void> releaseCaseProvisioning(UUID caseId) {
        return storage.releaseCaseProvisioning(
                Objects.requireNonNull(caseId, "Evidence case identifier cannot be null"));
    }

    public CompletableFuture<Optional<EvidenceSettings>> findSettings() {
        return storage.findSettings();
    }

    public CompletableFuture<Void> saveSettings(EvidenceSettings settings) {
        return storage.saveSettings(Objects.requireNonNull(settings, "Evidence settings cannot be null"));
    }

    public CompletableFuture<Boolean> beginSubmission(
            UUID caseId,
            long submissionId,
            long submittingDiscordUserId,
            boolean reviewerOverride,
            Instant startedAt,
            Duration staleUploadAge
    ) {
        Objects.requireNonNull(caseId, "Evidence case identifier cannot be null");
        requirePositive(submissionId, "Evidence submission identifier must be positive");
        requirePositive(submittingDiscordUserId, "Evidence submitter identifier must be positive");
        Objects.requireNonNull(startedAt, "Evidence submission start time cannot be null");
        requirePositiveDuration(staleUploadAge, "Evidence stale upload age must be positive");
        return storage.beginSubmission(
                caseId,
                submissionId,
                submittingDiscordUserId,
                reviewerOverride,
                startedAt,
                staleUploadAge
        );
    }

    public CompletableFuture<Boolean> beginSubmissionEdit(
            UUID caseId,
            long submissionId,
            long submittingDiscordUserId,
            boolean reviewerOverride,
            Instant startedAt,
            Duration staleUploadAge
    ) {
        Objects.requireNonNull(caseId, "Evidence case identifier cannot be null");
        requirePositive(submissionId, "Evidence submission identifier must be positive");
        requirePositive(submittingDiscordUserId, "Evidence submitter identifier must be positive");
        Objects.requireNonNull(startedAt, "Evidence submission edit start time cannot be null");
        requirePositiveDuration(staleUploadAge, "Evidence stale upload age must be positive");
        return storage.beginSubmissionEdit(
                caseId,
                submissionId,
                submittingDiscordUserId,
                reviewerOverride,
                startedAt,
                staleUploadAge
        );
    }

    public CompletableFuture<Optional<EvidenceCase>> completeSubmission(EvidenceSubmission submission) {
        Objects.requireNonNull(submission, "Evidence submission cannot be null");
        requireText(submission.incidentDescription(), "Evidence incident description cannot be blank");
        requireText(submission.proofDescription(), "Evidence proof description cannot be blank");
        Objects.requireNonNull(submission.additionalContext(), "Evidence additional context cannot be null");
        Objects.requireNonNull(submission.submittedAt(), "Evidence submission time cannot be null");
        if (submission.attachments().isEmpty()
                && (submission.externalLink() == null || submission.externalLink().isBlank())) {
            throw new IllegalArgumentException("Evidence submission requires a file or external link");
        }
        return storage.completeSubmission(submission);
    }

    public CompletableFuture<Void> failSubmission(UUID caseId, long submissionId) {
        Objects.requireNonNull(caseId, "Evidence case identifier cannot be null");
        requirePositive(submissionId, "Evidence submission identifier must be positive");
        return storage.failSubmission(caseId, submissionId);
    }

    public CompletableFuture<Optional<EvidenceCase>> reviewCase(
            UUID caseId,
            long reviewerDiscordUserId,
            EvidenceReviewDecision decision,
            String reviewReason,
            Instant reviewedAt
    ) {
        Objects.requireNonNull(caseId, "Evidence case identifier cannot be null");
        requirePositive(reviewerDiscordUserId, "Evidence reviewer identifier must be positive");
        Objects.requireNonNull(decision, "Evidence review decision cannot be null");
        Objects.requireNonNull(reviewedAt, "Evidence review time cannot be null");
        String normalizedReason = reviewReason == null ? "" : reviewReason.strip();
        if (decision == EvidenceReviewDecision.REQUEST_CHANGES && normalizedReason.isBlank()) {
            throw new IllegalArgumentException("Requested evidence changes require a reason");
        }
        return storage.reviewCase(
                caseId,
                reviewerDiscordUserId,
                decision,
                normalizedReason,
                reviewedAt
        );
    }

    public CompletableFuture<Optional<EvidenceCase>> editChangeRequest(
            UUID caseId,
            long changeRequestMessageId,
            long reviewerDiscordUserId,
            String reviewReason,
            Instant editedAt
    ) {
        Objects.requireNonNull(caseId, "Evidence case identifier cannot be null");
        requirePositive(changeRequestMessageId, "Evidence change request message identifier must be positive");
        requirePositive(reviewerDiscordUserId, "Evidence reviewer identifier must be positive");
        Objects.requireNonNull(editedAt, "Evidence change request edit time cannot be null");
        String normalizedReason = reviewReason == null ? "" : reviewReason.strip();
        if (normalizedReason.isBlank()) {
            throw new IllegalArgumentException("Edited evidence change request requires a reason");
        }
        return storage.editChangeRequest(
                caseId,
                changeRequestMessageId,
                reviewerDiscordUserId,
                normalizedReason,
                editedAt
        );
    }

    public CompletableFuture<Boolean> recordActiveChangeRequestMessage(UUID caseId, long messageId) {
        Objects.requireNonNull(caseId, "Evidence case identifier cannot be null");
        requirePositive(messageId, "Evidence change request message identifier must be positive");
        return storage.recordActiveChangeRequestMessage(caseId, messageId);
    }

    public CompletableFuture<OptionalLong> findActiveChangeRequestMessageId(UUID caseId) {
        return storage.findActiveChangeRequestMessageId(
                Objects.requireNonNull(caseId, "Evidence case identifier cannot be null")
        );
    }

    private static String normalizeIdentifier(String punishmentIdentifier) {
        requireText(punishmentIdentifier, "Punishment identifier cannot be blank");
        String normalizedIdentifier = punishmentIdentifier.toUpperCase(Locale.ROOT);
        if (!PUNISHMENT_IDENTIFIER_PATTERN.matcher(normalizedIdentifier).matches()) {
            throw new IllegalArgumentException("Punishment identifier is invalid");
        }
        return normalizedIdentifier;
    }

    private static void requireMinecraftName(String minecraftName) {
        if (minecraftName == null || !MINECRAFT_NAME_PATTERN.matcher(minecraftName).matches()) {
            throw new IllegalArgumentException("Minecraft name is invalid");
        }
    }

    private static void requireText(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requirePositive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requirePositiveDuration(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }
}
