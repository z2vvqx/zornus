package net.valoury.discord.api.ticket;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record Ticket(
        long ticketNumber,
        OptionalLong threadId,
        OptionalLong ownerDiscordUserId,
        long guildId,
        long parentChannelId,
        long staffRoleId,
        TicketStatus status,
        Instant createdAt,
        Optional<Instant> closedAt
) {
    public Ticket {
        if (ticketNumber <= 0) {
            throw new IllegalArgumentException("Ticket number must be positive");
        }
        if (guildId <= 0 || parentChannelId <= 0 || staffRoleId <= 0) {
            throw new IllegalArgumentException("Discord identifiers must be positive");
        }
        threadId = Objects.requireNonNull(threadId, "Thread identifier cannot be null");
        ownerDiscordUserId = Objects.requireNonNull(
                ownerDiscordUserId, "Owner Discord user identifier cannot be null");
        status = Objects.requireNonNull(status, "Ticket status cannot be null");
        createdAt = Objects.requireNonNull(createdAt, "Ticket creation time cannot be null");
        closedAt = Objects.requireNonNull(closedAt, "Ticket close time cannot be null");
        if (threadId.isPresent() && threadId.getAsLong() <= 0) {
            throw new IllegalArgumentException("Thread identifier must be positive");
        }
        if (ownerDiscordUserId.isPresent() && ownerDiscordUserId.getAsLong() <= 0) {
            throw new IllegalArgumentException("Owner Discord user identifier must be positive");
        }

        boolean active = status == TicketStatus.CREATING
                || status == TicketStatus.OPEN
                || status == TicketStatus.CLOSING;
        if (active != ownerDiscordUserId.isPresent()) {
            throw new IllegalArgumentException("Only active tickets can have a current owner");
        }
        if ((status == TicketStatus.CREATING) == threadId.isPresent()) {
            throw new IllegalArgumentException("Creating tickets cannot have a Discord thread");
        }
        if (status != TicketStatus.CREATING
                && status != TicketStatus.FAILED
                && threadId.isEmpty()) {
            throw new IllegalArgumentException("Activated tickets must have a Discord thread");
        }
        if (active == closedAt.isPresent()) {
            throw new IllegalArgumentException("Only terminal tickets can have a close time");
        }
    }
}
