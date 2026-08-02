package net.valoury.discord.bot.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordCommandRegistrarTest {
    @Test
    void createsTheCompleteGlobalCommandSet() {
        DiscordCommandRegistrar commandRegistrar = new DiscordCommandRegistrar();

        assertEquals(
                List.of("ticket", "link", "unlink"),
                commandRegistrar.createCommands().stream().map(command -> command.getName()).toList()
        );
    }
}
