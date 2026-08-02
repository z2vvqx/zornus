package net.valoury.discord.api.ticket;

public sealed interface ReserveTicketResult {
    record Reserved(Ticket ticket) implements ReserveTicketResult {
    }

    record AlreadyOwnsOpenTicket(Ticket ticket) implements ReserveTicketResult {
    }
}
