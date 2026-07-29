package net.valoury.guilds.proxy.utilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public final class GuildColorFormatter {

    private GuildColorFormatter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static @NonNull Component createColoredText(
            @NonNull String text,
            @NonNull String serializedGuildColor
    ) {
        return Component.text(text, resolveGuildColor(serializedGuildColor));
    }

    private static @NonNull NamedTextColor resolveGuildColor(@NonNull String serializedGuildColor) {
        String colorName = serializedGuildColor;
        if (serializedGuildColor.startsWith("<") && serializedGuildColor.endsWith(">")) {
            colorName = serializedGuildColor.substring(1, serializedGuildColor.length() - 1);
        }

        NamedTextColor guildColor = NamedTextColor.NAMES.value(colorName.toLowerCase(Locale.ROOT));
        return guildColor == null ? NamedTextColor.WHITE : guildColor;
    }
}
