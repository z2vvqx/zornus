package net.valoury.discord.bot.ticket.message;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.valoury.discord.bot.DiscordBotConstants;
import net.valoury.discord.bot.message.DiscordMessageFactory;
import net.valoury.discord.bot.ticket.TicketButtonIdentifier;

import java.util.Objects;

public final class TicketMessageFactory {
    private final DiscordMessageFactory discordMessageFactory;

    public TicketMessageFactory(DiscordMessageFactory discordMessageFactory) {
        this.discordMessageFactory = Objects.requireNonNull(
                discordMessageFactory,
                "Discord message factory cannot be null"
        );
    }

    public MessageCreateData ticketPanel(long staffRoleId) {
        return new MessageCreateBuilder()
                .setContent(DiscordBotConstants.TICKET_PANEL_TEXT)
                .setComponents(ActionRow.of(Button.primary(
                        TicketButtonIdentifier.create(staffRoleId),
                        DiscordBotConstants.TICKET_OPEN_BUTTON_LABEL
                )))
                .build();
    }

    public Container ticketOpening(long staffRoleId, long ownerDiscordUserId) {
        String openingText = """
                ## %s
                <@&%d>

                **OPENER:** <@%d>  **REASON:** %s  **ID:** `%d`

                %s
                """.formatted(
                        DiscordBotConstants.TICKET_OPENING_TITLE,
                        staffRoleId,
                        ownerDiscordUserId,
                        DiscordBotConstants.TICKET_OPENING_REASON,
                        ownerDiscordUserId,
                        DiscordBotConstants.TICKET_OPENING_TEXT
                ).strip();
        return discordMessageFactory.rawText(openingText);
    }
}
