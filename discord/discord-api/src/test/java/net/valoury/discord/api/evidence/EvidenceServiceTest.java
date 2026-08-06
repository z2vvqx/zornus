package net.valoury.discord.api.evidence;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceServiceTest {
    @Test
    void normalizesPresetCaseRequestsBeforeStorage() {
        RecordingEvidenceStorage storage = new RecordingEvidenceStorage();
        EvidenceService service = new EvidenceService(storage);
        UUID punishedPlayerId = UUID.randomUUID();

        service.createCase(new EvidenceCaseRequest(
                "ab12",
                punishedPlayerId,
                "Player_1",
                UUID.randomUUID(),
                123L,
                "Profane-Language",
                2,
                "mute",
                "Profane Language",
                Instant.parse("2026-08-05T10:00:00Z"),
                Instant.parse("2026-08-06T10:00:00Z")
        )).join();

        EvidenceCaseRequest storedRequest = storage.createdRequest;
        assertEquals("AB12", storedRequest.punishmentIdentifier());
        assertEquals("profane-language", storedRequest.presetName());
        assertEquals("MUTE", storedRequest.punishmentType());
    }

    @Test
    void rejectsInvalidCaseIdentityBeforeStorage() {
        EvidenceService service = new EvidenceService(new RecordingEvidenceStorage());

        assertThrows(IllegalArgumentException.class, () -> service.createCase(new EvidenceCaseRequest(
                "too-long",
                UUID.randomUUID(),
                "Invalid Player Name",
                null,
                -1L,
                "preset",
                0,
                "ban",
                "reason",
                Instant.now(),
                null
        )));
    }

    @Test
    void requiresAFileOrLinkForCompletedSubmissions() {
        EvidenceService service = new EvidenceService(new RecordingEvidenceStorage());

        assertThrows(IllegalArgumentException.class, () -> service.completeSubmission(new EvidenceSubmission(
                1L,
                UUID.randomUUID(),
                2L,
                "The player advertised another server.",
                "The chat log shows the advertisement.",
                "",
                null,
                3L,
                Instant.now(),
                List.of()
        )));
    }

    @Test
    void normalizesEditedChangeRequestsBeforeStorage() {
        RecordingEvidenceStorage storage = new RecordingEvidenceStorage();
        EvidenceService service = new EvidenceService(storage);

        service.editChangeRequest(
                UUID.randomUUID(),
                456L,
                123L,
                "  Include the surrounding messages.  ",
                Instant.now()
        ).join();

        assertEquals("Include the surrounding messages.", storage.editedReviewReason);
        assertEquals(456L, storage.editedChangeRequestMessageId);
        assertThrows(IllegalArgumentException.class, () -> service.editChangeRequest(
                UUID.randomUUID(),
                456L,
                123L,
                "   ",
                Instant.now()
        ));
        assertThrows(IllegalArgumentException.class, () -> service.editChangeRequest(
                UUID.randomUUID(),
                0L,
                123L,
                "Include the surrounding messages.",
                Instant.now()
        ));
    }

    private static final class RecordingEvidenceStorage implements EvidenceStorage {
        private EvidenceCaseRequest createdRequest;
        private long editedChangeRequestMessageId;
        private String editedReviewReason;

        @Override
        public CompletableFuture<EvidenceCase> createCase(EvidenceCaseRequest request) {
            this.createdRequest = request;
            return CompletableFuture.completedFuture(new EvidenceCase(
                    UUID.randomUUID(),
                    request.punishmentIdentifier(),
                    request.punishedPlayerId(),
                    request.punishedPlayerName(),
                    request.issuingPlayerId(),
                    request.issuingDiscordUserId(),
                    request.presetName(),
                    request.presetApplicationNumber(),
                    request.punishmentType(),
                    request.reason(),
                    request.punishmentCreatedAt(),
                    request.punishmentExpiresAt(),
                    EvidenceCaseStatus.PENDING_THREAD,
                    null,
                    null,
                    null,
                    null,
                    Instant.now()
            ));
        }

        @Override
        public CompletableFuture<Optional<EvidenceCase>> findCaseByIdentifier(String punishmentIdentifier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Optional<EvidenceCase>> findCaseByThreadId(long threadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Optional<EvidenceSubmission>> findLatestSubmission(UUID caseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<EvidenceCase>> claimPendingCases(
                Instant claimedAt,
                Duration staleClaimAge,
                int maximumCases
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Optional<EvidenceCase>> activateCase(
                UUID caseId,
                long guildId,
                long forumChannelId,
                long threadId,
                long starterMessageId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> releaseCaseProvisioning(UUID caseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Optional<EvidenceSettings>> findSettings() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> saveSettings(EvidenceSettings settings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Boolean> beginSubmission(
                UUID caseId,
                long submissionId,
                long submittingDiscordUserId,
                boolean reviewerOverride,
                Instant startedAt,
                Duration staleUploadAge
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Boolean> beginSubmissionEdit(
                UUID caseId,
                long submissionId,
                long submittingDiscordUserId,
                boolean reviewerOverride,
                Instant startedAt,
                Duration staleUploadAge
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Optional<EvidenceCase>> completeSubmission(EvidenceSubmission submission) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> failSubmission(UUID caseId, long submissionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Optional<EvidenceCase>> reviewCase(
                UUID caseId,
                long reviewerDiscordUserId,
                EvidenceReviewDecision decision,
                String reviewReason,
                Instant reviewedAt
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Optional<EvidenceCase>> editChangeRequest(
                UUID caseId,
                long changeRequestMessageId,
                long reviewerDiscordUserId,
                String reviewReason,
                Instant editedAt
        ) {
            this.editedChangeRequestMessageId = changeRequestMessageId;
            this.editedReviewReason = reviewReason;
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Boolean> recordActiveChangeRequestMessage(UUID caseId, long messageId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<OptionalLong> findActiveChangeRequestMessageId(UUID caseId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
