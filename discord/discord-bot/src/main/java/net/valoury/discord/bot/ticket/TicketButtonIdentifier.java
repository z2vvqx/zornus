package net.valoury.discord.bot.ticket;

import net.valoury.discord.bot.DiscordBotConstants;

import java.util.OptionalLong;

public final class TicketButtonIdentifier {
    private TicketButtonIdentifier() {
    }

    public static String create(long staffRoleId) {
        if (staffRoleId <= 0) {
            throw new IllegalArgumentException("Staff role identifier must be positive");
        }
        return DiscordBotConstants.TICKET_OPEN_BUTTON_PREFIX + staffRoleId;
    }

    public static boolean isTicketOpenButton(String componentIdentifier) {
        return componentIdentifier.startsWith(DiscordBotConstants.TICKET_OPEN_BUTTON_PREFIX);
    }

    public static OptionalLong parseStaffRoleId(String componentIdentifier) {
        if (!isTicketOpenButton(componentIdentifier)) {
            return OptionalLong.empty();
        }
        String rawIdentifier = componentIdentifier.substring(
                DiscordBotConstants.TICKET_OPEN_BUTTON_PREFIX.length());
        try {
            long staffRoleId = Long.parseLong(rawIdentifier);
            return staffRoleId > 0 ? OptionalLong.of(staffRoleId) : OptionalLong.empty();
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }
}
