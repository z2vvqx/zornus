package net.valoury.discord.bot.ticket;

import net.valoury.discord.bot.DiscordBotConstants;

public final class TicketThreadName {
    private TicketThreadName() {
    }

    public static String format(long ticketNumber) {
        if (ticketNumber <= 0) {
            throw new IllegalArgumentException("Ticket number must be positive");
        }
        return DiscordBotConstants.TICKET_THREAD_NAME_PREFIX + ticketNumber;
    }
}
