package net.valoury.discord.api.ticket;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class TicketService {
    private final TicketStorage storage;

    public TicketService(TicketStorage storage) {
        this.storage = Objects.requireNonNull(storage, "Ticket storage cannot be null");
    }

    public CompletableFuture<ReserveTicketResult> reserveTicket(
            long ownerDiscordUserId,
            long guildId,
            long parentChannelId,
            long staffRoleId
    ) {
        requireDiscordIdentifier(ownerDiscordUserId, "Ticket owner");
        requireDiscordIdentifier(guildId, "Guild");
        requireDiscordIdentifier(parentChannelId, "Parent channel");
        requireDiscordIdentifier(staffRoleId, "Staff role");
        return storage.reserveTicket(ownerDiscordUserId, guildId, parentChannelId, staffRoleId);
    }

    public CompletableFuture<Optional<Ticket>> activateTicket(long ticketNumber, long threadId) {
        if (ticketNumber <= 0) {
            throw new IllegalArgumentException("Ticket number must be positive");
        }
        requireDiscordIdentifier(threadId, "Thread");
        return storage.activateTicket(ticketNumber, threadId);
    }

    public CompletableFuture<Void> failTicketCreation(long ticketNumber) {
        if (ticketNumber <= 0) {
            throw new IllegalArgumentException("Ticket number must be positive");
        }
        return storage.failTicketCreation(ticketNumber);
    }

    public CompletableFuture<Optional<Ticket>> findOpenTicketByThread(long threadId) {
        requireDiscordIdentifier(threadId, "Thread");
        return storage.findOpenTicketByThread(threadId);
    }

    public CompletableFuture<BeginTicketCloseResult> beginTicketClose(long threadId) {
        requireDiscordIdentifier(threadId, "Thread");
        return storage.beginTicketClose(threadId);
    }

    public CompletableFuture<Boolean> completeTicketClose(long threadId) {
        requireDiscordIdentifier(threadId, "Thread");
        return storage.completeTicketClose(threadId);
    }

    public CompletableFuture<Boolean> restoreOpenTicket(long threadId) {
        requireDiscordIdentifier(threadId, "Thread");
        return storage.restoreOpenTicket(threadId);
    }

    public CompletableFuture<AssignTicketResult> assignTicket(long threadId, long selectedDiscordUserId) {
        requireDiscordIdentifier(threadId, "Thread");
        requireDiscordIdentifier(selectedDiscordUserId, "Selected user");
        return storage.assignTicket(threadId, selectedDiscordUserId);
    }

    private static void requireDiscordIdentifier(long identifier, String description) {
        if (identifier <= 0) {
            throw new IllegalArgumentException(description + " identifier must be positive");
        }
    }
}
