package net.valoury.discord.bot.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordCommandRegistrarTest {
    @Test
    void createsTheSharedNormalGuildCommandSet() {
        DiscordCommandRegistrar commandRegistrar = new DiscordCommandRegistrar();

        assertEquals(
                List.of("ticket", "link", "unlink"),
                commandRegistrar.createSharedGuildCommands().stream()
                        .map(command -> command.getName())
                        .toList()
        );
    }

    @Test
    void createsTheStaffGuildCommandSetWithEvidence() {
        DiscordCommandRegistrar commandRegistrar = new DiscordCommandRegistrar();

        assertEquals(
                List.of("ticket", "link", "unlink", "evidence"),
                commandRegistrar.createStaffGuildCommands().stream()
                        .map(command -> command.getName())
                        .toList()
        );
    }
}
