package net.valoury.discord.api.ticket;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void acceptsCreatingOpenAndClosedStates() {
        assertDoesNotThrow(() -> ticket(
                OptionalLong.empty(), OptionalLong.of(10), TicketStatus.CREATING, Optional.empty()));
        assertDoesNotThrow(() -> ticket(
                OptionalLong.of(20), OptionalLong.of(10), TicketStatus.OPEN, Optional.empty()));
        assertDoesNotThrow(() -> ticket(
                OptionalLong.of(20), OptionalLong.empty(), TicketStatus.CLOSED, Optional.of(CREATED_AT)));
    }

    @Test
    void rejectsMissingActiveOwner() {
        assertThrows(IllegalArgumentException.class, () -> ticket(
                OptionalLong.of(20), OptionalLong.empty(), TicketStatus.OPEN, Optional.empty()));
    }

    @Test
    void rejectsThreadOnCreatingTicket() {
        assertThrows(IllegalArgumentException.class, () -> ticket(
                OptionalLong.of(20), OptionalLong.of(10), TicketStatus.CREATING, Optional.empty()));
    }

    @Test
    void rejectsCloseTimeOnOpenTicket() {
        assertThrows(IllegalArgumentException.class, () -> ticket(
                OptionalLong.of(20), OptionalLong.of(10), TicketStatus.OPEN, Optional.of(CREATED_AT)));
    }

    private static Ticket ticket(
            OptionalLong threadId,
            OptionalLong ownerDiscordUserId,
            TicketStatus status,
            Optional<Instant> closedAt
    ) {
        return new Ticket(
                1,
                threadId,
                ownerDiscordUserId,
                30,
                40,
                50,
                status,
                CREATED_AT,
                closedAt
        );
    }
}
