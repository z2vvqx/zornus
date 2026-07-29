package net.valoury.bloodstone.server;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.valoury.shared.utilities.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class BloodstoneText {

    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();
    private static final int DEFAULT_TITLE_FADE_IN_TICKS = 10;
    private static final int DEFAULT_TITLE_STAY_TICKS = 70;
    private static final int DEFAULT_TITLE_FADE_OUT_TICKS = 20;

    private BloodstoneText() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static @NonNull Component deserialize(
            @NonNull String template,
            @NonNull TagResolver... resolvers
    ) {
        return resolvers.length == 0
                ? StringUtils.deserialize(template)
                : StringUtils.deserialize(template, TagResolver.resolver(resolvers));
    }

    public static @NonNull String legacy(
            @NonNull String template,
            @NonNull TagResolver... resolvers
    ) {
        return legacy(deserialize(template, resolvers));
    }

    public static @NonNull String legacy(@NonNull Component component) {
        return LEGACY_SECTION.serialize(component);
    }

    public static @NonNull List<Component> deserializeLines(
            @NonNull List<String> templates,
            @NonNull TagResolver... resolvers
    ) {
        return templates.stream()
                .map(template -> deserialize(template, resolvers))
                .toList();
    }

    public static @NonNull Component legacyComponent(@NonNull String legacyText) {
        return LEGACY_SECTION.deserialize(legacyText);
    }

    public static @NonNull Component ampersandComponent(
            @NonNull String legacyText
    ) {
        return LEGACY_AMPERSAND.deserialize(legacyText);
    }

    public static void sendMessage(
            @NonNull CommandSender sender,
            @NonNull String template,
            @NonNull TagResolver... resolvers
    ) {
        sendMessage(sender, deserialize(template, resolvers));
    }

    public static void sendMessage(
            @NonNull CommandSender sender,
            @NonNull Component message
    ) {
        sender.sendMessage(message);
    }

    public static void sendActionBar(
            @NonNull Player player,
            @NonNull String template,
            @NonNull TagResolver... resolvers
    ) {
        player.sendActionBar(deserialize(template, resolvers));
    }

    public static void showTitle(
            @NonNull Player player,
            @NonNull Component title,
            @NonNull Component subtitle
    ) {
        player.showTitle(Title.title(
                title,
                subtitle,
                DEFAULT_TITLE_FADE_IN_TICKS,
                DEFAULT_TITLE_STAY_TICKS,
                DEFAULT_TITLE_FADE_OUT_TICKS
        ));
    }
}
