package net.valoury.discord.bot;

import net.dv8tion.jda.api.JDA;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.api.ticket.TicketService;
import net.valoury.discord.bot.command.DiscordCommandRegistrar;
import net.valoury.discord.bot.interaction.DiscordInteractionResponder;
import net.valoury.discord.bot.link.listener.LinkInteractionListener;
import net.valoury.discord.bot.message.DiscordMessageFactory;
import net.valoury.discord.bot.ticket.listener.TicketInteractionListener;
import net.valoury.discord.bot.ticket.message.TicketMessageFactory;
import net.valoury.discord.bot.ticket.service.TicketManagementService;
import net.valoury.discord.bot.ticket.service.TicketOpeningService;
import net.valoury.discord.internal.InternalConstants;
import net.valoury.discord.internal.storage.DiscordPostgresStorage;

import java.util.concurrent.CompletableFuture;

public final class DiscordBotModule implements AutoCloseable {
    private final DiscordPostgresStorage storage;
    private final TicketInteractionListener ticketInteractionListener;
    private final LinkInteractionListener linkInteractionListener;
    private final DiscordCommandRegistrar commandRegistrar;

    public DiscordBotModule() {
        this.storage = new DiscordPostgresStorage(
                InternalConstants.POSTGRESQL_URL,
                InternalConstants.POSTGRESQL_USER,
                InternalConstants.POSTGRESQL_PASSWORD
        );
        try {
            TicketService ticketService = new TicketService(storage);
            AccountLinkService accountLinkService = new AccountLinkService(storage);
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
            this.commandRegistrar = new DiscordCommandRegistrar();
        } catch (RuntimeException exception) {
            storage.close();
            throw exception;
        }
    }

    public TicketInteractionListener ticketInteractionListener() {
        return ticketInteractionListener;
    }

    public LinkInteractionListener linkInteractionListener() {
        return linkInteractionListener;
    }

    public CompletableFuture<Void> initialize(JDA discordClient) {
        return commandRegistrar.register(discordClient);
    }

    @Override
    public void close() {
        storage.close();
    }
}
