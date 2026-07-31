package net.valoury.shared.utilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerNameFormatterTest {

    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();

    @Test
    void suffixFormattingContinuesIntoPlayerName() {
        Component playerName = PlayerNameFormatter.formatSuffixBeforeName(
                "&6[VIP] &b",
                Component.text("PlayerOne")
        );

        assertEquals("§6[VIP] §bPlayerOne", LEGACY_SECTION.serialize(playerName));
    }

    @Test
    void emptySuffixLeavesPlayerNameUnchanged() {
        Component playerName = Component.text("PlayerOne");

        assertEquals(playerName, PlayerNameFormatter.formatSuffixBeforeName(null, playerName));
        assertEquals(playerName, PlayerNameFormatter.formatSuffixBeforeName("", playerName));
    }
}
