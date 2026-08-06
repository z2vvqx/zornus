package net.valoury.discord.bot;

import net.dv8tion.jda.api.JDA;
import net.valoury.discord.api.evidence.EvidenceService;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.api.ticket.TicketService;
import net.valoury.discord.bot.command.DiscordCommandRegistrar;
import net.valoury.discord.bot.evidence.listener.EvidenceInteractionListener;
import net.valoury.discord.bot.evidence.message.EvidenceMessageFactory;
import net.valoury.discord.bot.evidence.modal.EvidenceModalFactory;
import net.valoury.discord.bot.evidence.service.EvidenceFileTransferService;
import net.valoury.discord.bot.evidence.service.EvidenceForumProvisioningService;
import net.valoury.discord.bot.evidence.service.EvidenceProvisioningWorker;
import net.valoury.discord.bot.evidence.service.EvidenceReviewService;
import net.valoury.discord.bot.evidence.service.EvidenceSubmissionService;
import net.valoury.discord.bot.evidence.service.EvidenceThreadService;
import net.valoury.discord.bot.interaction.DiscordInteractionResponder;
import net.valoury.discord.bot.link.listener.LinkInteractionListener;
import net.valoury.discord.bot.message.DiscordMessageFactory;
import net.valoury.discord.bot.ticket.listener.TicketInteractionListener;
import net.valoury.discord.bot.ticket.message.TicketMessageFactory;
import net.valoury.discord.bot.ticket.service.TicketManagementService;
import net.valoury.discord.bot.ticket.service.TicketOpeningService;
import net.valoury.discord.internal.InternalConstants;
import net.valoury.discord.internal.storage.DiscordPostgresStorage;
import net.valoury.discord.internal.storage.EvidencePostgresStorage;

import java.util.concurrent.CompletableFuture;

public final class DiscordBotModule implements AutoCloseable {
    private final DiscordPostgresStorage storage;
    private final EvidencePostgresStorage evidenceStorage;
    private final EvidenceFileTransferService evidenceFileTransferService;
    private final EvidenceProvisioningWorker evidenceProvisioningWorker;
    private final TicketInteractionListener ticketInteractionListener;
    private final LinkInteractionListener linkInteractionListener;
    private final EvidenceInteractionListener evidenceInteractionListener;
    private final DiscordCommandRegistrar commandRegistrar;

    public DiscordBotModule() {
        this.storage = new DiscordPostgresStorage(
                InternalConstants.POSTGRESQL_URL,
                InternalConstants.POSTGRESQL_USER,
                InternalConstants.POSTGRESQL_PASSWORD
        );
        EvidencePostgresStorage initializedEvidenceStorage = null;
        EvidenceFileTransferService initializedFileTransferService = null;
        EvidenceProvisioningWorker initializedProvisioningWorker = null;
        try {
            initializedEvidenceStorage = new EvidencePostgresStorage(
                    InternalConstants.POSTGRESQL_URL,
                    InternalConstants.POSTGRESQL_USER,
                    InternalConstants.POSTGRESQL_PASSWORD
            );
            this.evidenceStorage = initializedEvidenceStorage;
            TicketService ticketService = new TicketService(storage);
            AccountLinkService accountLinkService = new AccountLinkService(storage);
            EvidenceService evidenceService = new EvidenceService(evidenceStorage);
            DiscordMessageFactory discordMessageFactory = new DiscordMessageFactory();
            DiscordInteractionResponder interactionResponder = new DiscordInteractionResponder(
                    discordMessageFactory
            );
            TicketMessageFactory ticketMessageFactory = new TicketMessageFactory(discordMessageFactory);
            TicketOpeningService openingService = new TicketOpeningService(ticketService, ticketMessageFactory);
            TicketManagementService managementService = new TicketManagementService(ticketService);
            this.ticketInteractionListener = new TicketInteractionListener(
                    openingService,
                    managementService,
                    interactionResponder
            );
            this.linkInteractionListener = new LinkInteractionListener(
                    accountLinkService,
                    interactionResponder
            );
            EvidenceMessageFactory evidenceMessageFactory = new EvidenceMessageFactory();
            EvidenceThreadService evidenceThreadService = new EvidenceThreadService(evidenceMessageFactory);
            initializedFileTransferService = new EvidenceFileTransferService();
            this.evidenceFileTransferService = initializedFileTransferService;
            EvidenceSubmissionService evidenceSubmissionService = new EvidenceSubmissionService(
                    evidenceService,
                    evidenceFileTransferService,
                    evidenceMessageFactory,
                    evidenceThreadService
            );
            EvidenceReviewService evidenceReviewService = new EvidenceReviewService(
                    evidenceService,
                    evidenceThreadService
            );
            this.evidenceInteractionListener = new EvidenceInteractionListener(
                    evidenceService,
                    evidenceSubmissionService,
                    evidenceReviewService,
                    new EvidenceModalFactory(),
                    interactionResponder
            );
            initializedProvisioningWorker = new EvidenceProvisioningWorker(
                    new EvidenceForumProvisioningService(evidenceService, evidenceMessageFactory)
            );
            this.evidenceProvisioningWorker = initializedProvisioningWorker;
            this.commandRegistrar = new DiscordCommandRegistrar();
        } catch (RuntimeException exception) {
            if (initializedProvisioningWorker != null) {
                closeAfterInitializationFailure(initializedProvisioningWorker, exception);
            }
            if (initializedFileTransferService != null) {
                closeAfterInitializationFailure(initializedFileTransferService, exception);
            }
            if (initializedEvidenceStorage != null) {
                closeAfterInitializationFailure(initializedEvidenceStorage, exception);
            }
            closeAfterInitializationFailure(storage, exception);
            throw exception;
        }
    }

    public TicketInteractionListener ticketInteractionListener() {
        return ticketInteractionListener;
    }

    public LinkInteractionListener linkInteractionListener() {
        return linkInteractionListener;
    }

    public EvidenceInteractionListener evidenceInteractionListener() {
        return evidenceInteractionListener;
    }

    public CompletableFuture<Void> initialize(JDA discordClient) {
        return commandRegistrar.register(discordClient).thenRun(() -> evidenceProvisioningWorker.start(discordClient));
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        try {
            evidenceProvisioningWorker.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            evidenceFileTransferService.close();
        } catch (RuntimeException exception) {
            failure = accumulate(failure, exception);
        }
        try {
            evidenceStorage.close();
        } catch (RuntimeException exception) {
            failure = accumulate(failure, exception);
        }
        try {
            storage.close();
        } catch (RuntimeException exception) {
            failure = accumulate(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException accumulate(RuntimeException failure, RuntimeException additionalFailure) {
        if (failure == null) {
            return additionalFailure;
        }
        failure.addSuppressed(additionalFailure);
        return failure;
    }

    private static void closeAfterInitializationFailure(AutoCloseable resource, RuntimeException failure) {
        try {
            resource.close();
        } catch (Exception cleanupException) {
            failure.addSuppressed(cleanupException);
        }
    }
}
