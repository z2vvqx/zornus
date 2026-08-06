package net.valoury.discord.bot.evidence.service;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceReviewDecision;
import net.valoury.discord.api.evidence.EvidenceService;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EvidenceReviewService {
    private final EvidenceService evidenceService;
    private final EvidenceThreadService threadService;

    public EvidenceReviewService(EvidenceService evidenceService, EvidenceThreadService threadService) {
        this.evidenceService = Objects.requireNonNull(evidenceService, "Evidence service cannot be null");
        this.threadService = Objects.requireNonNull(threadService, "Evidence thread service cannot be null");
    }

    public CompletableFuture<String> accept(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long reviewerDiscordUserId
    ) {
        return evidenceService.reviewCase(
                        evidenceCase.caseId(),
                        reviewerDiscordUserId,
                        EvidenceReviewDecision.ACCEPT,
                        "",
                        Instant.now()
                )
                .thenCompose(reviewedCase -> reviewedCase
                        .map(value -> threadService.accept(threadChannel, value)
                                .thenApply(ignored -> EvidenceBotConstants.REVIEW_ACCEPTED))
                        .orElseGet(() -> CompletableFuture.completedFuture(
                                EvidenceBotConstants.REVIEW_NO_LONGER_PENDING)));
    }

    public CompletableFuture<String> requestChanges(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long reviewerDiscordUserId,
            String reason
    ) {
        String normalizedReason = reason.strip();
        return evidenceService.reviewCase(
                        evidenceCase.caseId(),
                        reviewerDiscordUserId,
                        EvidenceReviewDecision.REQUEST_CHANGES,
                        normalizedReason,
                        Instant.now()
                )
                .thenCompose(reviewedCase -> reviewedCase
                        .map(value -> threadService.requestChanges(
                                        threadChannel,
                                        value,
                                        reviewerDiscordUserId,
                                        normalizedReason
                                )
                                .thenCompose(message -> evidenceService.recordActiveChangeRequestMessage(
                                                value.caseId(),
                                                message.getIdLong()
                                        )
                                        .thenCompose(recorded -> recorded
                                                ? CompletableFuture.completedFuture(
                                                        EvidenceBotConstants.REVIEW_CHANGES_REQUESTED)
                                                : threadService.disableChangeRequestEdit(message, value.caseId())
                                                        .thenApply(ignored -> EvidenceBotConstants.OPERATION_FAILED))))
                        .orElseGet(() -> CompletableFuture.completedFuture(
                                EvidenceBotConstants.REVIEW_NO_LONGER_PENDING)));
    }

    public CompletableFuture<Boolean> isActiveChangeRequestMessage(UUID caseId, long messageId) {
        return evidenceService.findActiveChangeRequestMessageId(caseId)
                .thenApply(activeMessageId -> activeMessageId.isPresent()
                        && activeMessageId.getAsLong() == messageId);
    }

    public CompletableFuture<String> editChangeRequest(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long changeRequestMessageId,
            long reviewerDiscordUserId,
            String reason
    ) {
        String normalizedReason = reason.strip();
        return evidenceService.editChangeRequest(
                        evidenceCase.caseId(),
                        changeRequestMessageId,
                        reviewerDiscordUserId,
                        normalizedReason,
                        Instant.now()
                )
                .thenCompose(editedCase -> editedCase
                        .map(value -> threadService.editChangeRequest(
                                        threadChannel,
                                        value,
                                        changeRequestMessageId,
                                        reviewerDiscordUserId,
                                        normalizedReason
                                )
                                .thenApply(ignored -> EvidenceBotConstants.REVIEW_CHANGE_REQUEST_EDITED))
                        .orElseGet(() -> CompletableFuture.completedFuture(
                                EvidenceBotConstants.REVIEW_NO_LONGER_PENDING)));
    }
}
