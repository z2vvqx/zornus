package net.valoury.discord.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordBotConstantsTest {
    @Test
    void feedbackUsesCanonicalStatusPrefixes() {
        assertEquals("<a:valorV:1533039959651516498> - ", DiscordBotConstants.SUCCESS_FEEDBACK_PREFIX);
        assertEquals("<a:valorX:1533039960708350032> - ", DiscordBotConstants.FAILURE_FEEDBACK_PREFIX);
        assertTrue(DiscordBotConstants.LINK_ALREADY_LINKED.startsWith(
                DiscordBotConstants.FAILURE_FEEDBACK_PREFIX
        ));
    }
}
