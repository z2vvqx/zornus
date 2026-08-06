package net.valoury.discord.bot.evidence.service;

import net.dv8tion.jda.api.JDA;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static net.valoury.discord.bot.async.CompletionExceptionUnwrapper.unwrap;

public final class EvidenceProvisioningWorker implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceProvisioningWorker.class);

    private final EvidenceForumProvisioningService provisioningService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("discord-evidence-provisioning-").factory()
    );
    private volatile boolean closed;

    public EvidenceProvisioningWorker(EvidenceForumProvisioningService provisioningService) {
        this.provisioningService = Objects.requireNonNull(
                provisioningService,
                "Evidence provisioning service cannot be null"
        );
    }

    public void start(JDA discordClient) {
        Objects.requireNonNull(discordClient, "Discord client cannot be null");
        scheduler.execute(() -> runOnce(discordClient));
    }

    private void runOnce(JDA discordClient) {
        if (closed) {
            return;
        }
        provisioningService.provisionPendingCases(discordClient).whenComplete((ignored, exception) -> {
            if (exception != null) {
                LOGGER.error("Failed to poll pending Discord evidence cases", unwrap(exception));
            }
            if (!closed) {
                scheduler.schedule(
                        () -> runOnce(discordClient),
                        EvidenceBotConstants.PROVISIONING_POLL_INTERVAL.toMillis(),
                        TimeUnit.MILLISECONDS
                );
            }
        });
    }

    @Override
    public void close() {
        closed = true;
        scheduler.shutdownNow();
    }
}
