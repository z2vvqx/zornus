package net.valoury.discord.bot.link.command;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCommandFactoryTest {
    @Test
    void createsGuildAndBotDirectMessageLinkCommandWithRequiredCode() {
        SlashCommandData command = new LinkCommandFactory().createLinkCommand();
        OptionData codeOption = command.getOptions().getFirst();

        assertEquals("link", command.getName());
        assertEquals(
                Set.of(InteractionContextType.GUILD, InteractionContextType.BOT_DM),
                command.getContexts()
        );
        assertEquals(1, command.getOptions().size());
        assertEquals("code", codeOption.getName());
        assertEquals(OptionType.STRING, codeOption.getType());
        assertTrue(codeOption.isRequired());
    }

    @Test
    void createsGuildAndBotDirectMessageUnlinkCommandWithoutArguments() {
        SlashCommandData command = new LinkCommandFactory().createUnlinkCommand();

        assertEquals("unlink", command.getName());
        assertEquals(
                Set.of(InteractionContextType.GUILD, InteractionContextType.BOT_DM),
                command.getContexts()
        );
        assertTrue(command.getOptions().isEmpty());
    }
}
