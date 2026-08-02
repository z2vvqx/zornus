package net.valoury.shared.utilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NonNull;

public final class SocialRequestActions {

    private static final String CHECKMARK = "✔";
    private static final String CROSSMARK = "✘";

    private SocialRequestActions() {
    }

    public static @NonNull Component checkmarkAction(@NonNull String command) {
        return Component.text(CHECKMARK, NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand(command));
    }

    public static @NonNull Component crossmarkAction(@NonNull String command) {
        return Component.text(CROSSMARK, NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand(command));
    }
}
