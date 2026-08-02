package net.valoury.discord.api.ticket;

public sealed interface BeginTicketCloseResult {
    record Ready(Ticket ticket) implements BeginTicketCloseResult {
    }

    record TicketNotFound() implements BeginTicketCloseResult {
    }

    record MissingOwner() implements BeginTicketCloseResult {
    }

    record AlreadyClosing() implements BeginTicketCloseResult {
    }
}
