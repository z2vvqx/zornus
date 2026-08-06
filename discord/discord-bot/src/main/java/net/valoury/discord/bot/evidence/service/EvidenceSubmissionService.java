package net.valoury.discord.bot.evidence.service;

import net.dv8tion.jda.api.components.ResolvedMedia;
import net.dv8tion.jda.api.components.filedisplay.FileDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.valoury.discord.api.evidence.EvidenceAttachment;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceService;
import net.valoury.discord.api.evidence.EvidenceSubmission;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import net.valoury.discord.bot.evidence.message.EvidenceMessageFactory;
import net.valoury.discord.bot.evidence.message.PreparedEvidenceFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.valoury.discord.bot.async.CompletionExceptionUnwrapper.unwrap;

public final class EvidenceSubmissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceSubmissionService.class);

    private final EvidenceService evidenceService;
    private final EvidenceFileTransferService fileTransferService;
    private final EvidenceMessageFactory messageFactory;
    private final EvidenceThreadService threadService;

    public EvidenceSubmissionService(
            EvidenceService evidenceService,
            EvidenceFileTransferService fileTransferService,
            EvidenceMessageFactory messageFactory,
            EvidenceThreadService threadService
    ) {
        this.evidenceService = Objects.requireNonNull(evidenceService, "Evidence service cannot be null");
        this.fileTransferService = Objects.requireNonNull(
                fileTransferService,
                "Evidence file transfer service cannot be null"
        );
        this.messageFactory = Objects.requireNonNull(messageFactory, "Evidence message factory cannot be null");
        this.threadService = Objects.requireNonNull(threadService, "Evidence thread service cannot be null");
    }

    public CompletableFuture<String> submit(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long submissionId,
            long submittingDiscordUserId,
            boolean reviewerOverride,
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            List<Message.Attachment> attachments
    ) {
        Objects.requireNonNull(threadChannel, "Evidence thread cannot be null");
        Objects.requireNonNull(evidenceCase, "Evidence case cannot be null");
        Objects.requireNonNull(incidentDescription, "Evidence incident description cannot be null");
        Objects.requireNonNull(proofDescription, "Evidence proof description cannot be null");
        String normalizedAdditionalContext = normalizeOptionalText(additionalContext);
        List<Message.Attachment> immutableAttachments = List.copyOf(attachments);
        String normalizedLink;
        try {
            normalizedLink = normalizeExternalLink(externalLink);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.SUBMISSION_INVALID_LINK);
        }
        if (immutableAttachments.isEmpty() && normalizedLink == null) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.SUBMISSION_REQUIRES_PROOF);
        }

        Instant submissionTime = Instant.now();
        return evidenceService.beginSubmission(
                        evidenceCase.caseId(),
                        submissionId,
                        submittingDiscordUserId,
                        reviewerOverride,
                        submissionTime,
                        EvidenceBotConstants.UPLOAD_LEASE
                )
                .thenCompose(acquired -> acquired
                        ? prepareAndSubmit(
                                threadChannel,
                                evidenceCase,
                                submissionId,
                                submittingDiscordUserId,
                                incidentDescription.strip(),
                                proofDescription.strip(),
                                normalizedAdditionalContext.strip(),
                                normalizedLink,
                                immutableAttachments,
                                submissionTime
                        )
                        : CompletableFuture.completedFuture(
                                EvidenceBotConstants.CASE_NOT_ACCEPTING_SUBMISSIONS));
    }

    public CompletableFuture<String> edit(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long submissionId,
            long editingDiscordUserId,
            boolean reviewerOverride,
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            List<Message.Attachment> replacementAttachments
    ) {
        Objects.requireNonNull(threadChannel, "Evidence thread cannot be null");
        Objects.requireNonNull(evidenceCase, "Evidence case cannot be null");
        String normalizedIncidentDescription = normalizeOptionalText(incidentDescription);
        String normalizedProofDescription = normalizeOptionalText(proofDescription);
        String normalizedAdditionalContext = normalizeOptionalText(additionalContext);
        List<Message.Attachment> immutableAttachments = List.copyOf(replacementAttachments);
        String normalizedLink;
        try {
            normalizedLink = normalizeExternalLink(externalLink);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.SUBMISSION_INVALID_LINK);
        }
        Instant editTime = Instant.now();
        return evidenceService.beginSubmissionEdit(
                        evidenceCase.caseId(),
                        submissionId,
                        editingDiscordUserId,
                        reviewerOverride,
                        editTime,
                        EvidenceBotConstants.UPLOAD_LEASE
                )
                .thenCompose(acquired -> acquired
                        ? prepareAndEdit(
                                threadChannel,
                                evidenceCase,
                                submissionId,
                                editingDiscordUserId,
                                normalizedIncidentDescription,
                                normalizedProofDescription,
                                normalizedAdditionalContext,
                                normalizedLink,
                                immutableAttachments,
                                editTime
                        )
                        : CompletableFuture.completedFuture(EvidenceBotConstants.CASE_NOT_EDITABLE));
    }

    private CompletableFuture<String> prepareAndSubmit(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long submissionId,
            long submittingDiscordUserId,
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            List<Message.Attachment> attachments,
            Instant submissionTime
    ) {
        CompletableFuture<String> submission = fileTransferService.prepare(
                        attachments,
                        threadChannel.getGuild().getMaxFileSize()
                )
                .thenCompose(preparedFiles -> sendAndRecord(
                                threadChannel,
                                evidenceCase,
                                submissionId,
                                submittingDiscordUserId,
                                incidentDescription,
                                proofDescription,
                                additionalContext,
                                externalLink,
                                submissionTime,
                                preparedFiles
                        )
                        .whenComplete((ignored, exception) -> preparedFiles.close()));
        return recoverUploadFailure(evidenceCase, submissionId, "submit", submission);
    }

    private CompletableFuture<String> prepareAndEdit(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long submissionId,
            long editingDiscordUserId,
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            List<Message.Attachment> replacementAttachments,
            Instant editTime
    ) {
        CompletableFuture<String> edit = evidenceService.findLatestSubmission(evidenceCase.caseId())
                .thenCompose(latestSubmission -> latestSubmission
                        .map(existingSubmission -> {
                            if (isMissingRequiredReplacementAttachments(
                                    existingSubmission.attachments(),
                                    replacementAttachments
                            )) {
                                return evidenceService.failSubmission(evidenceCase.caseId(), submissionId)
                                        .thenApply(ignored ->
                                                EvidenceBotConstants.EDIT_REQUIRES_ATTACHMENT_REUPLOAD);
                            }
                            if (hasNoRequestedEdits(
                                    incidentDescription,
                                    proofDescription,
                                    additionalContext,
                                    externalLink,
                                    replacementAttachments
                            )) {
                                return evidenceService.failSubmission(evidenceCase.caseId(), submissionId)
                                        .thenApply(ignored -> EvidenceBotConstants.EDIT_REQUIRES_CHANGES);
                            }
                            return fileTransferService.prepare(
                                            replacementAttachments,
                                            threadChannel.getGuild().getMaxFileSize()
                                    )
                                    .thenCompose(preparedFiles -> editAndRecord(
                                                    threadChannel,
                                                    evidenceCase,
                                                    existingSubmission,
                                                    submissionId,
                                                    editingDiscordUserId,
                                                    keepExistingIfBlank(
                                                            incidentDescription,
                                                            existingSubmission.incidentDescription()
                                                    ),
                                                    keepExistingIfBlank(
                                                            proofDescription,
                                                            existingSubmission.proofDescription()
                                                    ),
                                                    keepExistingIfBlank(
                                                            additionalContext,
                                                            existingSubmission.additionalContext()
                                                    ),
                                                    externalLink == null
                                                            ? existingSubmission.externalLink()
                                                            : externalLink,
                                                    editTime,
                                                    preparedFiles
                                            )
                                            .whenComplete((ignored, exception) -> preparedFiles.close()));
                        })
                        .orElseGet(() -> CompletableFuture.failedFuture(
                                new IllegalStateException("Evidence case has no submission to edit"))));
        return recoverUploadFailure(evidenceCase, submissionId, "edit", edit);
    }

    private CompletableFuture<String> sendAndRecord(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long submissionId,
            long submittingDiscordUserId,
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            Instant submissionTime,
            PreparedEvidenceFiles preparedFiles
    ) {
        return threadChannel.sendMessage(messageFactory.submission(
                        evidenceCase,
                        submittingDiscordUserId,
                        incidentDescription,
                        proofDescription,
                        additionalContext,
                        externalLink,
                        preparedFiles.files()
                ))
                .submit()
                .thenCompose(message -> resolveUploadedAttachments(
                                threadChannel,
                                message,
                                preparedFiles.files()
                        )
                        .exceptionallyCompose(exception -> deleteFailedSubmissionMessage(
                                message,
                                unwrap(exception)
                        ))
                        .thenCompose(attachments -> evidenceService.completeSubmission(new EvidenceSubmission(
                                submissionId,
                                evidenceCase.caseId(),
                                submittingDiscordUserId,
                                incidentDescription,
                                proofDescription,
                                additionalContext,
                                externalLink,
                                message.getIdLong(),
                                submissionTime,
                                attachments
                        )))
                        .thenCompose(completedCase -> completedCase
                                .map(value -> updateThreadBestEffort(threadChannel, value, false))
                                .orElseGet(() -> deleteFailedSubmissionMessage(
                                        message,
                                        new IllegalStateException("Evidence submission was not accepted")
                                ))))
                .thenApply(ignored -> EvidenceBotConstants.SUBMISSION_COMPLETE);
    }

    private CompletableFuture<String> editAndRecord(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            EvidenceSubmission existingSubmission,
            long submissionId,
            long editingDiscordUserId,
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            Instant editTime,
            PreparedEvidenceFiles replacementFiles
    ) {
        return threadChannel.retrieveMessageById(existingSubmission.evidenceMessageId())
                .submit()
                .thenCompose(message -> message.editMessage(messageFactory.editedSubmission(
                            evidenceCase,
                            editingDiscordUserId,
                            incidentDescription,
                            proofDescription,
                            additionalContext,
                            externalLink,
                            replacementFiles.files()
                    )).submit())
                .thenCompose(message -> resolveUploadedAttachments(
                        threadChannel,
                        message,
                        replacementFiles.files()
                ))
                .thenCompose(attachments -> evidenceService.completeSubmission(new EvidenceSubmission(
                        submissionId,
                        evidenceCase.caseId(),
                        editingDiscordUserId,
                        incidentDescription,
                        proofDescription,
                        additionalContext,
                        externalLink,
                        existingSubmission.evidenceMessageId(),
                        editTime,
                        attachments
                )))
                .thenCompose(completedCase -> completedCase
                        .map(value -> updateThreadBestEffort(threadChannel, value, true))
                        .orElseGet(() -> CompletableFuture.failedFuture(
                                new IllegalStateException("Evidence edit was not accepted"))))
                .thenApply(ignored -> EvidenceBotConstants.EDIT_COMPLETE);
    }

    private CompletableFuture<String> recoverUploadFailure(
            EvidenceCase evidenceCase,
            long submissionId,
            String operation,
            CompletableFuture<String> upload
    ) {
        return upload.handle((feedback, exception) -> {
                    if (exception == null) {
                        return CompletableFuture.completedFuture(feedback);
                    }
                    Throwable failure = unwrap(exception);
                    LOGGER.error(
                            "Failed to {} Discord evidence for punishment {}",
                            operation,
                            evidenceCase.punishmentIdentifier(),
                            failure
                    );
                    return evidenceService.failSubmission(evidenceCase.caseId(), submissionId)
                            .exceptionally(releaseFailure -> {
                                failure.addSuppressed(unwrap(releaseFailure));
                                return null;
                            })
                            .thenApply(ignored -> feedbackFor(failure));
                })
                .thenCompose(result -> result);
    }

    private CompletableFuture<Void> updateThreadBestEffort(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            boolean evidenceWasEdited
    ) {
        CompletableFuture<Void> presentationUpdate = evidenceWasEdited
                ? threadService.markEditedEvidenceAwaitingReview(threadChannel, evidenceCase)
                : threadService.markAwaitingReview(threadChannel, evidenceCase);
        return presentationUpdate
                .exceptionally(exception -> {
                    LOGGER.error(
                            "Evidence submission was stored but thread presentation update failed for punishment {}",
                            evidenceCase.punishmentIdentifier(),
                            unwrap(exception)
                    );
                    return null;
                });
    }

    private static CompletableFuture<List<EvidenceAttachment>> resolveUploadedAttachments(
            ThreadChannel threadChannel,
            Message message,
            List<PreparedEvidenceFile> preparedFiles
    ) {
        if (preparedFiles.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            return CompletableFuture.completedFuture(mapAttachments(message, preparedFiles));
        } catch (IllegalStateException ignored) {
            return threadChannel.retrieveMessageById(message.getIdLong())
                    .submit()
                    .thenApply(retrievedMessage -> mapAttachments(retrievedMessage, preparedFiles));
        }
    }

    static List<EvidenceAttachment> mapAttachments(
            Message message,
            List<PreparedEvidenceFile> preparedFiles
    ) {
        // Components V2 file attachments are exposed through FileDisplay resolved media, not getAttachments().
        List<FileDisplay> fileDisplays = message.getComponentTree().findAll(FileDisplay.class);
        List<Message.Attachment> ordinaryAttachments = message.getAttachments();
        List<Long> attachmentIds = new ArrayList<>(preparedFiles.size());
        for (int index = 0; index < preparedFiles.size(); index++) {
            int fileIndex = index;
            int componentIdentifier = EvidenceBotConstants.FILE_COMPONENT_ID_BASE + fileIndex;
            FileDisplay fileDisplay = fileDisplays.stream()
                    .filter(component -> component.getUniqueId() == componentIdentifier)
                    .findFirst()
                    .orElseGet(() -> fileDisplays.size() == preparedFiles.size()
                            ? fileDisplays.get(fileIndex)
                            : null);
            ResolvedMedia resolvedMedia = fileDisplay == null ? null : fileDisplay.getResolvedMedia();
            Long attachmentId = resolvedMedia == null ? null : resolvedMedia.getAttachmentIdLong();
            if (attachmentId == null && ordinaryAttachments.size() == preparedFiles.size()) {
                attachmentId = ordinaryAttachments.get(fileIndex).getIdLong();
            }
            attachmentIds.add(attachmentId);
        }
        return mapAttachmentIds(attachmentIds, preparedFiles);
    }

    static List<EvidenceAttachment> mapAttachmentIds(
            List<Long> attachmentIds,
            List<PreparedEvidenceFile> preparedFiles
    ) {
        if (attachmentIds.size() != preparedFiles.size()) {
            throw new IllegalStateException("Discord response returned an unexpected evidence attachment count");
        }
        List<EvidenceAttachment> attachments = new ArrayList<>(preparedFiles.size());
        Set<Long> mappedAttachmentIds = new HashSet<>();
        for (int index = 0; index < preparedFiles.size(); index++) {
            PreparedEvidenceFile preparedFile = preparedFiles.get(index);
            Long attachmentId = attachmentIds.get(index);
            if (attachmentId == null || attachmentId <= 0 || !mappedAttachmentIds.add(attachmentId)) {
                throw new IllegalStateException(
                        "Discord response omitted resolved evidence attachment " + preparedFile.fileName());
            }
            attachments.add(new EvidenceAttachment(
                    attachmentId,
                    preparedFile.fileName(),
                    preparedFile.contentType(),
                    preparedFile.sizeBytes(),
                    preparedFile.sha256()
            ));
        }
        return List.copyOf(attachments);
    }

    private static <T> CompletableFuture<T> deleteFailedSubmissionMessage(
            Message message,
            Throwable failure
    ) {
        return message.delete().submit()
                .exceptionally(deleteFailure -> {
                    failure.addSuppressed(unwrap(deleteFailure));
                    return null;
                })
                .thenCompose(ignored -> CompletableFuture.failedFuture(failure));
    }

    static String normalizeExternalLink(String externalLink) {
        if (externalLink == null || externalLink.isBlank()) {
            return null;
        }
        try {
            URI parsedLink = new URI(externalLink.strip()).normalize();
            if (!"https".equalsIgnoreCase(parsedLink.getScheme())
                    || parsedLink.getHost() == null
                    || parsedLink.getHost().isBlank()
                    || parsedLink.getUserInfo() != null) {
                throw new IllegalArgumentException("Evidence link must be an HTTPS URL without credentials");
            }
            return new URI(
                    "https",
                    null,
                    parsedLink.getHost().toLowerCase(java.util.Locale.ROOT),
                    parsedLink.getPort(),
                    parsedLink.getPath(),
                    parsedLink.getQuery(),
                    parsedLink.getFragment()
            ).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Evidence link is invalid", exception);
        }
    }

    static String keepExistingIfBlank(String updatedValue, String existingValue) {
        return updatedValue == null || updatedValue.isBlank() ? existingValue : updatedValue.strip();
    }

    static boolean isMissingRequiredReplacementAttachments(
            List<EvidenceAttachment> existingAttachments,
            List<Message.Attachment> replacementAttachments
    ) {
        return !existingAttachments.isEmpty() && replacementAttachments.size() < existingAttachments.size();
    }

    static boolean hasNoRequestedEdits(
            String incidentDescription,
            String proofDescription,
            String additionalContext,
            String externalLink,
            List<Message.Attachment> replacementAttachments
    ) {
        return incidentDescription.isBlank()
                && proofDescription.isBlank()
                && additionalContext.isBlank()
                && externalLink == null
                && replacementAttachments.isEmpty();
    }

    static String normalizeOptionalText(String value) {
        return value == null ? "" : value;
    }

    private static String feedbackFor(Throwable failure) {
        if (failure instanceof EvidenceFileException fileException) {
            return switch (fileException.reason()) {
                case INVALID_TYPE -> EvidenceBotConstants.SUBMISSION_INVALID_FILE;
                case TOO_LARGE -> EvidenceBotConstants.SUBMISSION_TOO_LARGE;
                case TRANSFER_FAILED -> EvidenceBotConstants.OPERATION_FAILED;
            };
        }
        return EvidenceBotConstants.OPERATION_FAILED;
    }
}
