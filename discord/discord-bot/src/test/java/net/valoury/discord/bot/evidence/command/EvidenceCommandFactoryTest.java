package net.valoury.discord.bot.evidence.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceCommandFactoryTest {
    @Test
    void createsAdministratorOnlySetupCommand() {
        var command = new EvidenceCommandFactory().createEvidenceCommand();

        assertEquals("evidence", command.getName());
        assertEquals(
                Permission.ADMINISTRATOR.getRawValue(),
                command.getDefaultPermissions().getPermissionsRaw()
        );
        assertEquals(1, command.getSubcommands().size());
        assertEquals("setup", command.getSubcommands().getFirst().getName());
        assertEquals(
                java.util.List.of("forum", "reviewer-role"),
                command.getSubcommands().getFirst().getOptions().stream()
                        .map(OptionData::getName)
                        .toList()
        );
    }
}
