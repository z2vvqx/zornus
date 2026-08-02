package net.valoury.discord.bot.ticket.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.valoury.discord.bot.DiscordBotConstants;

public final class TicketCommandFactory {
    public SlashCommandData createTicketCommand() {
        OptionData selectedUser = new OptionData(
                OptionType.USER,
                "user",
                "The user to manage",
                true
        );
        OptionData staffRole = new OptionData(
                OptionType.ROLE,
                "staff-role",
                "The staff role alerted for tickets opened from this panel",
                true
        );

        return Commands.slash(DiscordBotConstants.TICKET_COMMAND_NAME, "Manage Discord tickets")
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                        new SubcommandData("panel", "Create the ticket button panel")
                                .addOptions(staffRole),
                        new SubcommandData("close", "Close the current ticket"),
                        new SubcommandData("assign", "Transfer ownership of the current ticket")
                                .addOptions(selectedUser),
                        new SubcommandData("add", "Add a user to the current ticket")
                                .addOptions(selectedUser),
                        new SubcommandData("remove", "Remove a user from the current ticket")
                                .addOptions(selectedUser)
                );
    }
}
