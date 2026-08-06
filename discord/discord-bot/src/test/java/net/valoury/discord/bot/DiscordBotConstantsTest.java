package net.valoury.discord.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordBotConstantsTest {
    @Test
    void feedbackUsesCanonicalStatusPrefixes() {
        assertEquals("<:valourycheckmark:1534501951797198959> ▸ ", DiscordBotConstants.SUCCESS_FEEDBACK_PREFIX);
        assertEquals("<:valourycrossmark:1534501953562738861> ▸ ", DiscordBotConstants.FAILURE_FEEDBACK_PREFIX);
        assertTrue(DiscordBotConstants.LINK_ALREADY_LINKED.startsWith(
                DiscordBotConstants.FAILURE_FEEDBACK_PREFIX
        ));
    }
}
