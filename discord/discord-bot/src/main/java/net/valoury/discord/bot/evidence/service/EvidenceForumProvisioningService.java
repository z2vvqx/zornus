package net.valoury.discord.bot.evidence.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumPost;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagSnowflake;
import net.valoury.discord.api.evidence.EvidenceCase;
import net.valoury.discord.api.evidence.EvidenceService;
import net.valoury.discord.api.evidence.EvidenceSettings;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import net.valoury.discord.bot.evidence.message.EvidenceMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static net.valoury.discord.bot.async.CompletionExceptionUnwrapper.unwrap;

public final class EvidenceForumProvisioningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceForumProvisioningService.class);

    private final EvidenceService evidenceService;
    private final EvidenceMessageFactory messageFactory;

    public EvidenceForumProvisioningService(
            EvidenceService evidenceService,
            EvidenceMessageFactory messageFactory
    ) {
        this.evidenceService = Objects.requireNonNull(evidenceService, "Evidence service cannot be null");
        this.messageFactory = Objects.requireNonNull(messageFactory, "Evidence message factory cannot be null");
    }

    public CompletableFuture<Void> provisionPendingCases(JDA discordClient) {
        Objects.requireNonNull(discordClient, "Discord client cannot be null");
        return evidenceService.findSettings().thenCompose(settings -> settings
                .map(value -> claimAndProvision(discordClient, value))
                .orElseGet(() -> CompletableFuture.completedFuture(null)));
    }

    private CompletableFuture<Void> claimAndProvision(JDA discordClient, EvidenceSettings settings) {
        ForumChannel forumChannel = discordClient.getForumChannelById(settings.forumChannelId());
        if (forumChannel == null || forumChannel.getGuild().getIdLong() != settings.guildId()) {
            return CompletableFuture.completedFuture(null);
        }
        return evidenceService.claimPendingCases(
                        Instant.now(),
                        EvidenceBotConstants.PROVISIONING_LEASE,
                        EvidenceBotConstants.PROVISIONING_BATCH_SIZE
                )
                .thenCompose(cases -> CompletableFuture.allOf(cases.stream()
                        .map(evidenceCase -> provision(forumChannel, evidenceCase))
                        .toArray(CompletableFuture[]::new)));
    }

    private CompletableFuture<Void> provision(ForumChannel forumChannel, EvidenceCase evidenceCase) {
        String threadName = messageFactory.threadName(evidenceCase);
        Optional<ThreadChannel> existingThread = forumChannel.getThreadChannels().stream()
                .filter(thread -> thread.getName().equals(threadName))
                .findFirst();
        CompletableFuture<ForumPost> forumPostFuture = existingThread
                .map(this::recoverForumPost)
                .orElseGet(() -> createForumPost(forumChannel, evidenceCase, threadName));
        return forumPostFuture.thenCompose(forumPost -> evidenceService.activateCase(
                                evidenceCase.caseId(),
                                forumChannel.getGuild().getIdLong(),
                                forumChannel.getIdLong(),
                                forumPost.getThreadChannel().getIdLong(),
                                forumPost.getMessage().getIdLong()
                        )
                        .thenCompose(activatedCase -> activatedCase
                                .map(value -> forumPost.getMessage()
                                        .editMessage(messageFactory.updatedStarter(value))
                                        .submit()
                                        .thenApply(ignored -> (Void) null)
                                        .exceptionally(exception -> {
                                            LOGGER.error(
                                                    "Evidence case {} was activated but its starter message update failed",
                                                    evidenceCase.punishmentIdentifier(),
                                                    unwrap(exception)
                                            );
                                            return null;
                                        }))
                                .orElseGet(() -> CompletableFuture.failedFuture(
                                        new IllegalStateException(
                                                "Evidence case was not activated after forum creation")))))
                .exceptionallyCompose(exception -> {
                    LOGGER.error(
                            "Failed to create evidence forum post for punishment {}",
                            evidenceCase.punishmentIdentifier(),
                            unwrap(exception)
                    );
                    return evidenceService.releaseCaseProvisioning(evidenceCase.caseId());
                });
    }

    private CompletableFuture<ForumPost> createForumPost(
            ForumChannel forumChannel,
            EvidenceCase evidenceCase,
        String threadName
    ) {
        var action = forumChannel.createForumPost(threadName, messageFactory.starter(evidenceCase));
        action.setTags(ForumTagSnowflake.fromId(EvidenceBotConstants.AWAITING_EVIDENCE_TAG_ID));
        return action.submit();
    }

    private CompletableFuture<ForumPost> recoverForumPost(ThreadChannel threadChannel) {
        return threadChannel.retrieveStartMessage().submit()
                .thenApply(message -> new ForumPost(message, threadChannel));
    }
}
