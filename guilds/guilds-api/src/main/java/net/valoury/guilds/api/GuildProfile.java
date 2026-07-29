package net.valoury.guilds.api;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * The immutable guild identity and display data exposed to game servers.
 *
 * @param guildId guild identifier
 * @param name guild name
 * @param tag guild tag
 * @param color MiniMessage color value
 */
public record GuildProfile(
        @NonNull UUID guildId,
        @NonNull String name,
        @NonNull String tag,
        @NonNull String color
) {
}
