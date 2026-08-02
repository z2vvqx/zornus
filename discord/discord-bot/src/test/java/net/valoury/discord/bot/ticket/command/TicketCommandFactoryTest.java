package net.valoury.discord.bot.ticket.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TicketCommandFactoryTest {
    @Test
    void createsGuildOnlyAdministratorTicketCommand() {
        SlashCommandData command = new TicketCommandFactory().createTicketCommand();

        assertEquals("ticket", command.getName());
        assertEquals(Set.of(InteractionContextType.GUILD), command.getContexts());
        assertNotNull(command.getDefaultPermissions().getPermissionsRaw());
        assertEquals(
                Permission.getRaw(Permission.ADMINISTRATOR),
                command.getDefaultPermissions().getPermissionsRaw().longValue()
        );
        assertEquals(
                List.of("panel", "close", "assign", "add", "remove"),
                command.getSubcommands().stream().map(subcommand -> subcommand.getName()).toList()
        );
    }
}
