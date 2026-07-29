package net.valoury.bloodstone.server.registrar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BloodstonePlaceholderRegistrarTest {

    @Test
    void legacyPlaceholderPrefixesRemainExact() {
        assertEquals("guild",
                BloodstonePlaceholderRegistrar.GUILD_IDENTIFIER);
        assertEquals("top.player.kills",
                BloodstonePlaceholderRegistrar.PLAYER_KILLS_IDENTIFIER);
        assertEquals("top.player.rampage",
                BloodstonePlaceholderRegistrar.PLAYER_RAMPAGE_IDENTIFIER);
        assertEquals("top.guild.kills",
                BloodstonePlaceholderRegistrar.GUILD_KILLS_IDENTIFIER);
        assertEquals("top.guild.rampage",
                BloodstonePlaceholderRegistrar.GUILD_RAMPAGE_IDENTIFIER);
        assertEquals("player.stats",
                BloodstonePlaceholderRegistrar.PLAYER_STATISTICS_IDENTIFIER);
    }
}
