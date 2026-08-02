package net.valoury.discord.bot.interaction;

import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.valoury.discord.bot.message.DiscordMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static net.valoury.discord.bot.async.CompletionExceptionUnwrapper.unwrap;

public final class DiscordInteractionResponder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordInteractionResponder.class);

    private final DiscordMessageFactory messageFactory;

    public DiscordInteractionResponder(DiscordMessageFactory messageFactory) {
        this.messageFactory = Objects.requireNonNull(messageFactory, "Discord message factory cannot be null");
    }

    public void respond(
            IReplyCallback interaction,
            String operationDescription,
            String failureFeedback,
            Supplier<CompletableFuture<String>> feedbackOperation
    ) {
        Objects.requireNonNull(interaction, "Discord interaction cannot be null");
        Objects.requireNonNull(feedbackOperation, "Discord feedback operation cannot be null");
        requireText(operationDescription, "Discord operation description cannot be blank");
        requireText(failureFeedback, "Discord failure feedback cannot be blank");

        long discordUserId = interaction.getUser().getIdLong();
        interaction.deferReply(true).queue(
                hook -> completeFeedback(
                        hook,
                        discordUserId,
                        operationDescription,
                        failureFeedback,
                        execute(feedbackOperation)
                ),
                exception -> LOGGER.error(
                        "Failed to acknowledge {} for Discord user {}",
                        operationDescription,
                        discordUserId,
                        unwrap(exception)
                )
        );
    }

    private void completeFeedback(
            InteractionHook hook,
            long discordUserId,
            String operationDescription,
            String failureFeedback,
            CompletableFuture<String> feedback
    ) {
        feedback.whenComplete((message, exception) -> {
            if (exception != null || message == null || message.isBlank()) {
                Throwable failure = exception == null
                        ? new IllegalStateException("Discord feedback operation returned a blank message")
                        : unwrap(exception);
                LOGGER.error(
                        "Failed to complete {} for Discord user {}",
                        operationDescription,
                        discordUserId,
                        failure
                );
                sendFeedback(hook, discordUserId, operationDescription, failureFeedback);
                return;
            }
            sendFeedback(hook, discordUserId, operationDescription, message);
        });
    }

    private void sendFeedback(
            InteractionHook hook,
            long discordUserId,
            String operationDescription,
            String feedback
    ) {
        hook.editOriginalComponents(messageFactory.rawText(feedback))
                .useComponentsV2()
                .setAllowedMentions(Collections.emptySet())
                .queue(
                        ignored -> {
                        },
                        exception -> LOGGER.error(
                                "Failed to send {} feedback to Discord user {}",
                                operationDescription,
                                discordUserId,
                                unwrap(exception)
                        )
                );
    }

    private static CompletableFuture<String> execute(
            Supplier<CompletableFuture<String>> feedbackOperation
    ) {
        try {
            return Objects.requireNonNull(
                    feedbackOperation.get(),
                    "Discord feedback operation cannot return null"
            );
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static void requireText(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

}
