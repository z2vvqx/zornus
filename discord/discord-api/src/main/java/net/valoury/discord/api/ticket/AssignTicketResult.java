package net.valoury.discord.api.ticket;

public sealed interface AssignTicketResult {
    record Assigned(long previousOwnerDiscordUserId, Ticket ticket) implements AssignTicketResult {
    }

    record TicketNotFound() implements AssignTicketResult {
    }

    record MissingOwner() implements AssignTicketResult {
    }

    record AlreadyOwner() implements AssignTicketResult {
    }

    record SelectedUserAlreadyOwnsOpenTicket(Ticket ticket) implements AssignTicketResult {
    }
}
