package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.guilds.api.GuildProfile;

import java.util.Locale;

final class BloodstoneGuildText {

    private static final TextColor DEFAULT_COLOR = NamedTextColor.DARK_GRAY;

    private BloodstoneGuildText() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    static Component tag(GuildProfile guildProfile) {
        return BloodstoneText.deserialize(
                "[<tag>]",
                Placeholder.unparsed("tag", guildProfile.tag())
        ).color(color(guildProfile.color()));
    }

    static Component tagDisplay(GuildProfile guildProfile) {
        return BloodstoneText.deserialize(
                " <guild>",
                Placeholder.component("guild", tag(guildProfile))
        );
    }

    static Component nameAndTag(GuildProfile guildProfile) {
        return BloodstoneText.deserialize(
                "<name> <guild>",
                Placeholder.unparsed("name", guildProfile.name()),
                Placeholder.component("guild", tag(guildProfile))
        ).color(color(guildProfile.color()));
    }

    private static TextColor color(String configuredColor) {
        if (configuredColor == null || configuredColor.isBlank()) {
            return DEFAULT_COLOR;
        }
        String normalized = configuredColor.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');
        if (normalized.startsWith("<") && normalized.endsWith(">")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        NamedTextColor namedColor = NamedTextColor.NAMES.value(normalized);
        if (namedColor != null) {
            return namedColor;
        }

        Component coloredMarker = configuredColor.indexOf('&') >= 0
                ? BloodstoneText.ampersandComponent(configuredColor + "x")
                : BloodstoneText.legacyComponent(configuredColor + "x");
        TextColor legacyColor = coloredMarker.color();
        return legacyColor == null
                ? DEFAULT_COLOR
                : legacyColor;
    }
}
