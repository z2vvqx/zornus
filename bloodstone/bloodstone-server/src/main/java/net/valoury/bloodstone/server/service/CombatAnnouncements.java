package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class CombatAnnouncements {

    private static final List<String> DOMINATION_CONTINUATIONS = List.of(
            "<killer> <green>has total control over <white><victim></white>!</green>",
            "<victim> <green>cannot escape <white><killer></white>'s dominance!</green>",
            "<killer> <green>refuses to let <white><victim></white> alive!</green>"
    );
    private static final List<String> REVENGE_MESSAGES = List.of(
            "<killer> <green>has taken <gold><bold>revenge</bold></gold> on <white><victim></white>!</green>",
            "<killer> <green>got <gold><bold>revenge</bold></gold> against <white><victim></white>!</green>",
            "<killer> <green>claimed <gold><bold>revenge</bold></gold> on <white><victim></white>!</green>"
    );
    private static final List<String> EXTENDED_RAMPAGE_TEXTS = List.of(
            "No end in sight for",
            "Slaughtering continues in",
            "Carnage never ends for"
    );

    private CombatAnnouncements() {
    }

    static @NonNull String domination(int count) {
        return switch (count) {
            case 4 -> "<killer> <green>is now <red><bold>dominating</bold></red> <white><victim></white>!</green>";
            case 8 -> "<killer> <green>is still dominating <white><victim></white>!</green>";
            case 12 -> "<killer> <green>won't let <white><victim></white> breathe!</green>";
            default -> randomEntry(DOMINATION_CONTINUATIONS);
        };
    }

    static @NonNull String revenge() {
        return randomEntry(REVENGE_MESSAGES);
    }

    static @NonNull RampageAnnouncement rampage(int rampage) {
        return switch (rampage) {
            case 5 -> new RampageAnnouncement("There's no escape from", NamedTextColor.WHITE);
            case 10 -> new RampageAnnouncement("Don't underestimate", NamedTextColor.DARK_AQUA);
            case 15 -> new RampageAnnouncement("No one stands against", NamedTextColor.BLUE);
            case 25 -> new RampageAnnouncement("Expect no mercy from", NamedTextColor.DARK_RED);
            case 50, 75, 100 -> new RampageAnnouncement(randomEntry(EXTENDED_RAMPAGE_TEXTS), NamedTextColor.DARK_RED);
            default -> new RampageAnnouncement("Beware", NamedTextColor.DARK_RED);
        };
    }

    private static String randomEntry(List<String> entries) {
        return entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
    }

    record RampageAnnouncement(@NonNull String text, @NonNull TextColor weaponColor) {
    }
}
