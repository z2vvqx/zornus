package net.valoury.discord.bot.evidence.service;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagSnowflake;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import net.valoury.discord.bot.evidence.EvidenceButtonIdentifier;
import net.valoury.discord.bot.evidence.message.EvidenceMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.valoury.discord.bot.async.CompletionExceptionUnwrapper.unwrap;

public final class EvidenceThreadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceThreadService.class);
    private static final int MAXIMUM_CHANGE_REQUEST_HISTORY_MESSAGES = 1_000;

    private final EvidenceMessageFactory messageFactory;

    public EvidenceThreadService(EvidenceMessageFactory messageFactory) {
        this.messageFactory = Objects.requireNonNull(messageFactory, "Evidence message factory cannot be null");
    }

    public CompletableFuture<Void> markAwaitingReview(ThreadChannel threadChannel, EvidenceCase evidenceCase) {
        return updateThread(
                threadChannel,
                evidenceCase,
                EvidenceBotConstants.AWAITING_REVIEW_TAG_ID,
                false,
                false
        );
    }

    public CompletableFuture<Void> markEditedEvidenceAwaitingReview(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase
    ) {
        return markAwaitingReview(threadChannel, evidenceCase)
                .thenCompose(ignored -> disableChangeRequestEdits(threadChannel, evidenceCase.caseId()));
    }

    public CompletableFuture<Void> accept(ThreadChannel threadChannel, EvidenceCase evidenceCase) {
        return updateThread(threadChannel, evidenceCase, EvidenceBotConstants.ACCEPTED_TAG_ID, true, true);
    }

    public CompletableFuture<Message> requestChanges(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long reviewerDiscordUserId,
            String reason
    ) {
        return updateThread(threadChannel, evidenceCase, EvidenceBotConstants.NEEDS_CHANGES_TAG_ID, false, false)
                .thenCompose(ignored -> disableChangeRequestEdits(threadChannel, evidenceCase.caseId())
                        .exceptionally(exception -> {
                            LOGGER.error(
                                    "Failed to disable earlier change request controls for punishment {}",
                                    evidenceCase.punishmentIdentifier(),
                                    unwrap(exception)
                            );
                            return null;
                        }))
                .thenCompose(ignored -> threadChannel.sendMessage(
                        messageFactory.changesRequested(evidenceCase, reviewerDiscordUserId, reason)
                ).submit());
    }

    public CompletableFuture<Void> disableChangeRequestEdits(ThreadChannel threadChannel, UUID caseId) {
        return threadChannel.getIterableHistory()
                .takeAsync(MAXIMUM_CHANGE_REQUEST_HISTORY_MESSAGES)
                .thenCompose(messages -> {
                    List<CompletableFuture<Void>> edits = messages.stream()
                            .filter(message -> hasEnabledChangeRequestEdit(message, caseId))
                            .map(message -> disableChangeRequestEdit(message, caseId))
                            .toList();
                    return CompletableFuture.allOf(edits.toArray(CompletableFuture[]::new));
                });
    }

    public CompletableFuture<Void> disableChangeRequestEdit(Message message, UUID caseId) {
        return message.editMessage(messageFactory.disabledChangeRequestEdit(
                        message.getComponentTree(),
                        caseId
                ))
                .submit()
                .thenApply(ignored -> null);
    }

    public CompletableFuture<Void> editChangeRequest(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long changeRequestMessageId,
            long reviewerDiscordUserId,
            String reason
    ) {
        return threadChannel.retrieveMessageById(changeRequestMessageId)
                .submit()
                .thenCompose(message -> message.editMessage(
                        messageFactory.editedChangeRequest(evidenceCase, reviewerDiscordUserId, reason)
                ).submit())
                .thenApply(ignored -> null);
    }

    static boolean hasEnabledChangeRequestEdit(Message message, UUID caseId) {
        String editButtonIdentifier = EvidenceButtonIdentifier.editChangeRequest(caseId);
        return message.getComponentTree().findAll(Button.class).stream()
                .anyMatch(button -> button.isEnabled()
                        && Objects.equals(button.getCustomId(), editButtonIdentifier));
    }

    private CompletableFuture<Void> updateThread(
            ThreadChannel threadChannel,
            EvidenceCase evidenceCase,
            long tagId,
            boolean locked,
            boolean archived
    ) {
        Objects.requireNonNull(threadChannel, "Evidence thread cannot be null");
        if (evidenceCase.starterMessageId() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Evidence case has no starter message"));
        }
        CompletableFuture<Void> editStarter = threadChannel
                .retrieveMessageById(evidenceCase.starterMessageId())
                .submit()
                .thenCompose(message -> message.editMessage(messageFactory.updatedStarter(evidenceCase)).submit())
                .thenApply(ignored -> null);

        var manager = threadChannel.getManager().setLocked(locked).setArchived(archived);
        if (threadChannel.getParentChannel() instanceof ForumChannel) {
            manager.setAppliedTags(ForumTagSnowflake.fromId(tagId));
        }
        return editStarter.thenCompose(ignored -> manager.submit()).thenApply(ignored -> null);
    }
}
