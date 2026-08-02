package net.valoury.discord.api.ticket;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface TicketStorage extends AutoCloseable {
    CompletableFuture<ReserveTicketResult> reserveTicket(
            long ownerDiscordUserId,
            long guildId,
            long parentChannelId,
            long staffRoleId
    );

    CompletableFuture<Optional<Ticket>> activateTicket(long ticketNumber, long threadId);

    CompletableFuture<Void> failTicketCreation(long ticketNumber);

    CompletableFuture<Optional<Ticket>> findOpenTicketByThread(long threadId);

    CompletableFuture<BeginTicketCloseResult> beginTicketClose(long threadId);

    CompletableFuture<Boolean> completeTicketClose(long threadId);

    CompletableFuture<Boolean> restoreOpenTicket(long threadId);

    CompletableFuture<AssignTicketResult> assignTicket(long threadId, long selectedDiscordUserId);

    @Override
    void close();
}
