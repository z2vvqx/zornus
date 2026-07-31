package net.valoury.friends.proxy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.valoury.shared.utilities.PlayerNameFormatter;
import net.valoury.shared.utilities.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendMessageFormatTest {

    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();

    @Test
    void usesSuffixBeforePlayerNameInSentAndReceivedMessages() {
        Component playerName = PlayerNameFormatter.formatSuffixBeforeName(
                "&6[VIP] &b",
                Component.text("PlayerOne")
        );
        TagResolver resolver = TagResolver.resolver(
                Placeholder.component("target", playerName),
                Placeholder.component("sender", playerName),
                Placeholder.unparsed("message", "hello")
        );

        String sentMessage = LEGACY_SECTION.serialize(
                StringUtils.deserialize(FriendProxyConstants.MESSAGE_SENT_FORMAT, resolver)
        );
        String receivedMessage = LEGACY_SECTION.serialize(
                StringUtils.deserialize(FriendProxyConstants.MESSAGE_RECEIVED_FORMAT, resolver)
        );

        assertTrue(sentMessage.contains("\u00A76[VIP] \u00A7bPlayerOne"));
        assertTrue(receivedMessage.contains("\u00A76[VIP] \u00A7bPlayerOne"));
    }
}
