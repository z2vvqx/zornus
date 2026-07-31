package net.valoury.guilds.proxy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.valoury.guilds.proxy.utilities.GuildColorFormatter;
import net.valoury.shared.utilities.PlayerNameFormatter;
import net.valoury.shared.utilities.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildChatFormatTest {

    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();

    @Test
    void usesSuffixBeforePlayerNameAndWhiteMessage() {
        Component playerName = PlayerNameFormatter.formatSuffixBeforeName(
                "&6[VIP] ",
                Component.text("PlayerOne")
        );
        Component message = StringUtils.deserialize(
                GuildProxyConstants.NOTIFICATION_CHAT_FORMAT,
                TagResolver.resolver(
                        Placeholder.component(
                                "guild",
                                GuildColorFormatter.createColoredText("Guild", "blue")
                        ),
                        Placeholder.component("playername", playerName),
                        Placeholder.component(
                                "rank_tag",
                                Component.text("[L] ", NamedTextColor.GRAY)
                        ),
                        Placeholder.unparsed("message", "hello")
                )
        );

        String legacyMessage = LEGACY_SECTION.serialize(message);
        assertTrue(legacyMessage.contains("§6[VIP] PlayerOne"));
        assertTrue(legacyMessage.endsWith("§fhello"));
    }
}
