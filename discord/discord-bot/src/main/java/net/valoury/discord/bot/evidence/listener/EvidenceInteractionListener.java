package net.valoury.discord.bot.evidence.listener;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceCaseStatus;
import net.valoury.discord.api.evidence.EvidenceService;
import net.valoury.discord.api.evidence.EvidenceSettings;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import net.valoury.discord.bot.evidence.EvidenceButtonIdentifier;
import net.valoury.discord.bot.evidence.EvidenceModalIdentifier;
import net.valoury.discord.bot.evidence.modal.EvidenceModalFactory;
import net.valoury.discord.bot.evidence.service.EvidenceReviewService;
import net.valoury.discord.bot.evidence.service.EvidenceSubmissionService;
import net.valoury.discord.bot.interaction.DiscordInteractionResponder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.valoury.discord.bot.async.CompletionExceptionUnwrapper.unwrap;

public final class EvidenceInteractionListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceInteractionListener.class);
    private static final EnumSet<Permission> REQUIRED_BOT_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_SEND_IN_THREADS,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_HISTORY,
            Permission.CREATE_PUBLIC_THREADS,
            Permission.MANAGE_THREADS
    );

    private final EvidenceService evidenceService;
    private final EvidenceSubmissionService submissionService;
    private final EvidenceReviewService reviewService;
    private final EvidenceModalFactory modalFactory;
    private final DiscordInteractionResponder interactionResponder;

    public EvidenceInteractionListener(
            EvidenceService evidenceService,
            EvidenceSubmissionService submissionService,
            EvidenceReviewService reviewService,
            EvidenceModalFactory modalFactory,
            DiscordInteractionResponder interactionResponder
    ) {
        this.evidenceService = Objects.requireNonNull(evidenceService, "Evidence service cannot be null");
        this.submissionService = Objects.requireNonNull(
                submissionService,
                "Evidence submission service cannot be null"
        );
        this.reviewService = Objects.requireNonNull(reviewService, "Evidence review service cannot be null");
        this.modalFactory = Objects.requireNonNull(modalFactory, "Evidence modal factory cannot be null");
        this.interactionResponder = Objects.requireNonNull(
                interactionResponder,
                "Discord interaction responder cannot be null"
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!EvidenceBotConstants.COMMAND_NAME.equals(event.getName())) {
            return;
        }
        interactionResponder.respond(
                event,
                "evidence setup command",
                EvidenceBotConstants.OPERATION_FAILED,
                () -> configureEvidence(event)
        );
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentIdentifier = event.getComponentId();
        Optional<UUID> submitCaseId = EvidenceButtonIdentifier.parseSubmit(componentIdentifier);
        if (submitCaseId.isPresent()) {
            openSubmissionModal(event, submitCaseId.get());
            return;
        }
        Optional<UUID> editCaseId = EvidenceButtonIdentifier.parseEdit(componentIdentifier);
        if (editCaseId.isPresent()) {
            if (isChangeRequestNotification(event)) {
                openChangeRequestEditModal(event, editCaseId.get());
            } else {
                openEditModal(event, editCaseId.get());
            }
            return;
        }
        Optional<UUID> editChangeRequestCaseId = EvidenceButtonIdentifier.parseEditChangeRequest(
                componentIdentifier
        );
        if (editChangeRequestCaseId.isPresent()) {
            openChangeRequestEditModal(event, editChangeRequestCaseId.get());
            return;
        }
        Optional<UUID> acceptedCaseId = EvidenceButtonIdentifier.parseAccept(componentIdentifier);
        if (acceptedCaseId.isPresent()) {
            interactionResponder.respond(
                    event,
                    "evidence acceptance",
                    EvidenceBotConstants.OPERATION_FAILED,
                    () -> review(event, acceptedCaseId.get(), true, "")
            );
            return;
        }
        EvidenceButtonIdentifier.parseRequestChanges(componentIdentifier)
                .ifPresent(caseId -> openChangesModal(event, caseId));
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        Optional<UUID> submissionCaseId = EvidenceModalIdentifier.parseSubmission(event.getModalId());
        if (submissionCaseId.isPresent()) {
            interactionResponder.respond(
                    event,
                    "evidence submission",
                    EvidenceBotConstants.OPERATION_FAILED,
                    () -> submitEvidence(event, submissionCaseId.get())
            );
            return;
        }
        Optional<UUID> editCaseId = EvidenceModalIdentifier.parseEdit(event.getModalId());
        if (editCaseId.isPresent()) {
            interactionResponder.respond(
                    event,
                    "evidence edit",
                    EvidenceBotConstants.OPERATION_FAILED,
                    () -> editEvidence(event, editCaseId.get())
            );
            return;
        }
        Optional<EvidenceModalIdentifier.ChangeRequestEditTarget> changeRequestEditTarget =
                EvidenceModalIdentifier.parseEditChangeRequest(event.getModalId());
        if (changeRequestEditTarget.isPresent()) {
            interactionResponder.respond(
                    event,
                    "evidence change request edit",
                    EvidenceBotConstants.OPERATION_FAILED,
                    () -> editChangeRequest(event, changeRequestEditTarget.get())
            );
            return;
        }
        EvidenceModalIdentifier.parseRequestChanges(event.getModalId()).ifPresent(caseId ->
                interactionResponder.respond(
                        event,
                        "evidence change request",
                        EvidenceBotConstants.OPERATION_FAILED,
                        () -> review(
                                event,
                                caseId,
                                false,
                                stringValue(event, EvidenceBotConstants.CHANGES_REASON_FIELD)
                        )
                ));
    }

    private CompletableFuture<String> configureEvidence(SlashCommandInteractionEvent event) {
        Member administrator = event.getMember();
        if (administrator == null || !administrator.hasPermission(Permission.ADMINISTRATOR)) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.ADMINISTRATOR_ONLY);
        }
        OptionMapping forumOption = event.getOption("forum");
        OptionMapping reviewerRoleOption = event.getOption("reviewer-role");
        if (forumOption == null || !(forumOption.getAsChannel() instanceof ForumChannel forumChannel)) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.SETTINGS_INVALID_FORUM);
        }
        if (forumChannel.getGuild().getPublicRole().hasPermission(forumChannel, Permission.VIEW_CHANNEL)) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.SETTINGS_INVALID_FORUM);
        }
        if (forumChannel.isTagRequired()
                && forumChannel.getAvailableTagById(EvidenceBotConstants.AWAITING_EVIDENCE_TAG_ID) == null) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.SETTINGS_MISSING_REQUIRED_TAG);
        }
        Role reviewerRole = reviewerRoleOption == null ? null : reviewerRoleOption.getAsRole();
        if (reviewerRole == null || reviewerRole.getGuild().getIdLong() != forumChannel.getGuild().getIdLong()) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.SETTINGS_INVALID_REVIEWER_ROLE);
        }
        if (!forumChannel.getGuild().getSelfMember().hasPermission(forumChannel, REQUIRED_BOT_PERMISSIONS)) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.SETTINGS_MISSING_BOT_PERMISSIONS);
        }
        if (!reviewerRole.hasPermission(forumChannel, Permission.VIEW_CHANNEL)) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.SETTINGS_INVALID_REVIEWER_ROLE);
        }
        return evidenceService.saveSettings(new EvidenceSettings(
                        forumChannel.getGuild().getIdLong(),
                        forumChannel.getIdLong(),
                        reviewerRole.getIdLong(),
                        Instant.now()
                ))
                .thenApply(ignored -> EvidenceBotConstants.SETTINGS_SAVED);
    }

    private void openSubmissionModal(ButtonInteractionEvent event, UUID caseId) {
        resolveCase(event.getChannel(), caseId)
                .thenCombine(isReviewer(event.getMember()), CaseAuthorization::new)
                .whenComplete((authorization, exception) -> {
                    if (exception != null) {
                        logAndReply(event, "open evidence submission modal", exception);
                        return;
                    }
                    if (authorization.evidenceCase().isEmpty()) {
                        reply(event, EvidenceBotConstants.CASE_NOT_FOUND);
                        return;
                    }
                    EvidenceCase evidenceCase = authorization.evidenceCase().get();
                    if (!canSubmit(evidenceCase, event.getUser().getIdLong(), authorization.reviewer())) {
                        reply(event, EvidenceBotConstants.CASE_NOT_ASSIGNED);
                        return;
                    }
                    if (!acceptsSubmission(evidenceCase.status())) {
                        reply(
                                event,
                                isEditable(evidenceCase.status())
                                        ? EvidenceBotConstants.CASE_ALREADY_SUBMITTED
                                        : EvidenceBotConstants.CASE_NOT_ACCEPTING_SUBMISSIONS
                        );
                        return;
                    }
                    event.replyModal(modalFactory.submission(evidenceCase)).queue(
                            ignored -> {
                            },
                            failure -> LOGGER.error(
                                    "Failed to open evidence submission modal for Discord user {}",
                                    event.getUser().getIdLong(),
                                    unwrap(failure)
                            )
                    );
                });
    }

    private void openEditModal(ButtonInteractionEvent event, UUID caseId) {
        resolveCase(event.getChannel(), caseId)
                .thenCombine(isReviewer(event.getMember()), CaseAuthorization::new)
                .whenComplete((authorization, exception) -> {
                    if (exception != null) {
                        logAndReply(event, "open evidence edit modal", exception);
                        return;
                    }
                    if (authorization.evidenceCase().isEmpty()) {
                        reply(event, EvidenceBotConstants.CASE_NOT_FOUND);
                        return;
                    }
                    EvidenceCase evidenceCase = authorization.evidenceCase().get();
                    if (!canSubmit(evidenceCase, event.getUser().getIdLong(), authorization.reviewer())) {
                        reply(event, EvidenceBotConstants.CASE_NOT_ASSIGNED);
                        return;
                    }
                    if (!isEditable(evidenceCase.status())) {
                        reply(event, EvidenceBotConstants.CASE_NOT_EDITABLE);
                        return;
                    }
                    evidenceService.findLatestSubmission(caseId).whenComplete((submission, submissionException) -> {
                        if (submissionException != null) {
                            logAndReply(event, "load evidence for edit modal", submissionException);
                            return;
                        }
                        if (submission.isEmpty()) {
                            reply(event, EvidenceBotConstants.CASE_NOT_EDITABLE);
                            return;
                        }
                        int previousAttachmentCount = submission.get().attachments().size();
                        if (previousAttachmentCount > EvidenceBotConstants.MAXIMUM_ATTACHMENTS) {
                            LOGGER.error(
                                    "Evidence case {} has {} attachments, exceeding the supported maximum of {}",
                                    caseId,
                                    previousAttachmentCount,
                                    EvidenceBotConstants.MAXIMUM_ATTACHMENTS
                            );
                            reply(event, EvidenceBotConstants.OPERATION_FAILED);
                            return;
                        }
                        event.replyModal(modalFactory.edit(evidenceCase, previousAttachmentCount)).queue(
                                ignored -> {
                                },
                                failure -> LOGGER.error(
                                        "Failed to open evidence edit modal for Discord user {}",
                                        event.getUser().getIdLong(),
                                        unwrap(failure)
                                )
                        );
                    });
                });
    }

    private void openChangesModal(ButtonInteractionEvent event, UUID caseId) {
        resolveCase(event.getChannel(), caseId)
                .thenCombine(isReviewer(event.getMember()), CaseAuthorization::new)
                .whenComplete((authorization, exception) -> {
                    if (exception != null) {
                        logAndReply(event, "open evidence changes modal", exception);
                        return;
                    }
                    if (!authorization.reviewer()) {
                        reply(event, EvidenceBotConstants.REVIEWER_ONLY);
                        return;
                    }
                    if (authorization.evidenceCase().isEmpty()) {
                        reply(event, EvidenceBotConstants.CASE_NOT_FOUND);
                        return;
                    }
                    EvidenceCase evidenceCase = authorization.evidenceCase().get();
                    if (evidenceCase.status() != EvidenceCaseStatus.AWAITING_REVIEW) {
                        reply(event, EvidenceBotConstants.REVIEW_NO_LONGER_PENDING);
                        return;
                    }
                    event.replyModal(modalFactory.requestChanges(evidenceCase)).queue(
                            ignored -> {
                            },
                            failure -> LOGGER.error(
                                    "Failed to open evidence changes modal for Discord user {}",
                                    event.getUser().getIdLong(),
                                    unwrap(failure)
                            )
                    );
                });
    }

    private void openChangeRequestEditModal(ButtonInteractionEvent event, UUID caseId) {
        resolveCase(event.getChannel(), caseId)
                .thenCombine(isReviewer(event.getMember()), CaseAuthorization::new)
                .whenComplete((authorization, exception) -> {
                    if (exception != null) {
                        logAndReply(event, "open evidence change request edit modal", exception);
                        return;
                    }
                    if (!authorization.reviewer()) {
                        reply(event, EvidenceBotConstants.REVIEWER_ONLY);
                        return;
                    }
                    if (authorization.evidenceCase().isEmpty()) {
                        reply(event, EvidenceBotConstants.CASE_NOT_FOUND);
                        return;
                    }
                    EvidenceCase evidenceCase = authorization.evidenceCase().get();
                    if (evidenceCase.status() != EvidenceCaseStatus.NEEDS_CHANGES) {
                        reply(event, EvidenceBotConstants.REVIEW_NO_LONGER_PENDING);
                        return;
                    }
                    reviewService.isActiveChangeRequestMessage(
                                    evidenceCase.caseId(),
                                    event.getMessageIdLong()
                            )
                            .whenComplete((activeChangeRequest, activeRequestFailure) -> {
                                if (activeRequestFailure != null) {
                                    logAndReply(
                                            event,
                                            "validate evidence change request edit",
                                            activeRequestFailure
                                    );
                                    return;
                                }
                                if (!activeChangeRequest) {
                                    reply(event, EvidenceBotConstants.REVIEW_NO_LONGER_PENDING);
                                    return;
                                }
                                event.replyModal(modalFactory.editChangeRequest(
                                        evidenceCase,
                                        event.getMessageIdLong()
                                )).queue(
                                        ignored -> {
                                        },
                                        failure -> LOGGER.error(
                                                "Failed to open evidence change request edit modal for Discord user {}",
                                                event.getUser().getIdLong(),
                                                unwrap(failure)
                                        )
                                );
                            });
                });
    }

    private CompletableFuture<String> submitEvidence(ModalInteractionEvent event, UUID caseId) {
        if (!(event.getChannel() instanceof ThreadChannel threadChannel)) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_WRONG_THREAD);
        }
        return resolveCase(threadChannel, caseId)
                .thenCombine(isReviewer(event.getMember()), CaseAuthorization::new)
                .thenCompose(authorization -> {
                    if (authorization.evidenceCase().isEmpty()) {
                        return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_NOT_FOUND);
                    }
                    EvidenceCase evidenceCase = authorization.evidenceCase().get();
                    if (!canSubmit(evidenceCase, event.getUser().getIdLong(), authorization.reviewer())) {
                        return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_NOT_ASSIGNED);
                    }
                    return submissionService.submit(
                            threadChannel,
                            evidenceCase,
                            event.getIdLong(),
                            event.getUser().getIdLong(),
                            authorization.reviewer(),
                            stringValue(event, EvidenceBotConstants.INCIDENT_FIELD),
                            stringValue(event, EvidenceBotConstants.PROOF_FIELD),
                            stringValue(event, EvidenceBotConstants.CONTEXT_FIELD),
                            stringValue(event, EvidenceBotConstants.LINK_FIELD),
                            attachmentValue(event, EvidenceBotConstants.FILES_FIELD)
                    );
                });
    }

    private CompletableFuture<String> editEvidence(ModalInteractionEvent event, UUID caseId) {
        if (!(event.getChannel() instanceof ThreadChannel threadChannel)) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_WRONG_THREAD);
        }
        return resolveCase(threadChannel, caseId)
                .thenCombine(isReviewer(event.getMember()), CaseAuthorization::new)
                .thenCompose(authorization -> {
                    if (authorization.evidenceCase().isEmpty()) {
                        return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_NOT_FOUND);
                    }
                    EvidenceCase evidenceCase = authorization.evidenceCase().get();
                    if (!canSubmit(evidenceCase, event.getUser().getIdLong(), authorization.reviewer())) {
                        return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_NOT_ASSIGNED);
                    }
                    return submissionService.edit(
                            threadChannel,
                            evidenceCase,
                            event.getIdLong(),
                            event.getUser().getIdLong(),
                            authorization.reviewer(),
                            stringValue(event, EvidenceBotConstants.INCIDENT_FIELD),
                            stringValue(event, EvidenceBotConstants.PROOF_FIELD),
                            stringValue(event, EvidenceBotConstants.CONTEXT_FIELD),
                            stringValue(event, EvidenceBotConstants.LINK_FIELD),
                            attachmentValue(event, EvidenceBotConstants.FILES_FIELD)
                    );
                });
    }

    private CompletableFuture<String> review(
            net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event,
            UUID caseId,
            boolean accept,
            String reason
    ) {
        if (!(event.getChannel() instanceof ThreadChannel threadChannel)) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_WRONG_THREAD);
        }
        return resolveCase(threadChannel, caseId)
                .thenCombine(isReviewer(event.getMember()), CaseAuthorization::new)
                .thenCompose(authorization -> {
                    if (!authorization.reviewer()) {
                        return CompletableFuture.completedFuture(EvidenceBotConstants.REVIEWER_ONLY);
                    }
                    if (authorization.evidenceCase().isEmpty()) {
                        return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_NOT_FOUND);
                    }
                    EvidenceCase evidenceCase = authorization.evidenceCase().get();
                    return accept
                            ? reviewService.accept(threadChannel, evidenceCase, event.getUser().getIdLong())
                            : reviewService.requestChanges(
                                    threadChannel,
                                    evidenceCase,
                                    event.getUser().getIdLong(),
                                    reason
                            );
                });
    }

    private CompletableFuture<String> editChangeRequest(
            ModalInteractionEvent event,
            EvidenceModalIdentifier.ChangeRequestEditTarget editTarget
    ) {
        if (!(event.getChannel() instanceof ThreadChannel threadChannel)) {
            return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_WRONG_THREAD);
        }
        return resolveCase(threadChannel, editTarget.caseId())
                .thenCombine(isReviewer(event.getMember()), CaseAuthorization::new)
                .thenCompose(authorization -> {
                    if (!authorization.reviewer()) {
                        return CompletableFuture.completedFuture(EvidenceBotConstants.REVIEWER_ONLY);
                    }
                    if (authorization.evidenceCase().isEmpty()) {
                        return CompletableFuture.completedFuture(EvidenceBotConstants.CASE_NOT_FOUND);
                    }
                    return reviewService.editChangeRequest(
                            threadChannel,
                            authorization.evidenceCase().get(),
                            editTarget.messageId(),
                            event.getUser().getIdLong(),
                            stringValue(event, EvidenceBotConstants.CHANGES_REASON_FIELD)
                    );
                });
    }

    private CompletableFuture<Optional<EvidenceCase>> resolveCase(
            net.dv8tion.jda.api.entities.channel.Channel channel,
            UUID expectedCaseId
    ) {
        if (!(channel instanceof ThreadChannel threadChannel)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return evidenceService.findCaseByThreadId(threadChannel.getIdLong())
                .thenApply(evidenceCase -> evidenceCase.filter(value -> value.caseId().equals(expectedCaseId)));
    }

    private CompletableFuture<Boolean> isReviewer(Member member) {
        if (member == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return CompletableFuture.completedFuture(true);
        }
        return evidenceService.findSettings().thenApply(settings -> settings
                .filter(value -> value.guildId() == member.getGuild().getIdLong())
                .map(value -> member.getRoles().stream()
                        .anyMatch(role -> role.getIdLong() == value.reviewerRoleId()))
                .orElse(false));
    }

    private static boolean canSubmit(EvidenceCase evidenceCase, long discordUserId, boolean reviewer) {
        return reviewer || Objects.equals(evidenceCase.issuingDiscordUserId(), discordUserId);
    }

    private static boolean acceptsSubmission(EvidenceCaseStatus status) {
        return status == EvidenceCaseStatus.AWAITING_EVIDENCE;
    }

    private static boolean isEditable(EvidenceCaseStatus status) {
        return status == EvidenceCaseStatus.AWAITING_REVIEW || status == EvidenceCaseStatus.NEEDS_CHANGES;
    }

    private static boolean isChangeRequestNotification(ButtonInteractionEvent event) {
        return event.getMessage().getComponentTree().findAll(TextDisplay.class).stream()
                .map(TextDisplay::getContent)
                .anyMatch(content -> content.startsWith("## Evidence changes requested"));
    }

    private static String stringValue(ModalInteractionEvent event, String componentIdentifier) {
        ModalMapping mapping = event.getValue(componentIdentifier);
        return mapping == null
                ? ""
                : Objects.requireNonNullElse(mapping.getAsOptionalString(), "");
    }

    private static List<net.dv8tion.jda.api.entities.Message.Attachment> attachmentValue(
            ModalInteractionEvent event,
            String componentIdentifier
    ) {
        ModalMapping mapping = event.getValue(componentIdentifier);
        return mapping == null ? List.of() : List.copyOf(mapping.getAsAttachmentList());
    }

    private void reply(ButtonInteractionEvent event, String feedback) {
        interactionResponder.reply(event, "evidence interaction", feedback);
    }

    private void logAndReply(ButtonInteractionEvent event, String operation, Throwable exception) {
        LOGGER.error(
                "Failed to {} for Discord user {}",
                operation,
                event.getUser().getIdLong(),
                unwrap(exception)
        );
        reply(event, EvidenceBotConstants.OPERATION_FAILED);
    }

    private record CaseAuthorization(Optional<EvidenceCase> evidenceCase, boolean reviewer) {
    }
}
