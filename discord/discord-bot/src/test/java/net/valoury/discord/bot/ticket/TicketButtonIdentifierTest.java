package net.valoury.discord.bot.ticket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketButtonIdentifierTest {
    @Test
    void roundTripsStaffRoleIdentifier() {
        String componentIdentifier = TicketButtonIdentifier.create(123456789L);

        assertEquals("ticket:open:123456789", componentIdentifier);
        assertEquals(123456789L, TicketButtonIdentifier.parseStaffRoleId(componentIdentifier).orElseThrow());
    }

    @Test
    void rejectsMalformedIdentifiers() {
        assertTrue(TicketButtonIdentifier.parseStaffRoleId("ticket:open:").isEmpty());
        assertTrue(TicketButtonIdentifier.parseStaffRoleId("ticket:open:-1").isEmpty());
        assertTrue(TicketButtonIdentifier.parseStaffRoleId("ticket:open:12:34").isEmpty());
        assertTrue(TicketButtonIdentifier.parseStaffRoleId("other:123").isEmpty());
    }
}
