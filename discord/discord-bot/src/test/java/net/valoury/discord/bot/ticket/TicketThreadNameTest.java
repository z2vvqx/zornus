package net.valoury.discord.bot.ticket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketThreadNameTest {
    @Test
    void usesSingleHashtagTicketFormat() {
        assertEquals("TICKET・#6", TicketThreadName.format(6));
    }

    @Test
    void rejectsNonPositiveTicketNumbers() {
        assertThrows(IllegalArgumentException.class, () -> TicketThreadName.format(0));
        assertThrows(IllegalArgumentException.class, () -> TicketThreadName.format(-1));
    }
}
