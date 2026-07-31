package net.valoury.shared.utilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class PlayerNameFormatter {

    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();

    private PlayerNameFormatter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static @NonNull Component formatSuffixBeforeName(
            @Nullable String suffix,
            @NonNull Component playerName
    ) {
        Objects.requireNonNull(playerName, "Player name cannot be null");
        if (suffix == null || suffix.isEmpty()) {
            return playerName;
        }
        return LEGACY_AMPERSAND.deserialize(
                suffix + LEGACY_AMPERSAND.serialize(playerName)
        );
    }
}
