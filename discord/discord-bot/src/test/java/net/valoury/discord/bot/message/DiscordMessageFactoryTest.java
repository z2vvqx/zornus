package net.valoury.discord.bot.message;

import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.container.Container;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscordMessageFactoryTest {
    private final DiscordMessageFactory messageFactory = new DiscordMessageFactory();

    @Test
    void rawTextUsesUnstyledComponentsV2Container() {
        Container container = messageFactory.rawText("Raw feedback");

        assertNull(container.getAccentColorRaw());
        assertFalse(container.isSpoiler());
        assertEquals(1, container.getComponents().size());
        assertEquals(Component.Type.TEXT_DISPLAY, container.getComponents().getFirst().getType());
        assertEquals("Raw feedback", container.getComponents().getFirst().asTextDisplay().getContent());
    }

    @Test
    void rawTextRejectsMissingContent() {
        assertThrows(IllegalArgumentException.class, () -> messageFactory.rawText(null));
        assertThrows(IllegalArgumentException.class, () -> messageFactory.rawText("   "));
    }
}
