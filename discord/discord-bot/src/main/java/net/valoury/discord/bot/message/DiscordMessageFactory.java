package net.valoury.discord.bot.message;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

public final class DiscordMessageFactory {
    public Container rawText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Discord message text cannot be blank");
        }
        return Container.of(TextDisplay.of(text));
    }
}
