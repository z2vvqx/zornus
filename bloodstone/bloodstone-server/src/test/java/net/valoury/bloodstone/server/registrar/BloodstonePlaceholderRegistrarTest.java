package net.valoury.bloodstone.server.registrar;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void dominatorMarkersUseThreeObfuscatedPipes() {
        assertEquals(" &c&k|||&r",
                BloodstonePlaceholderRegistrar.LIGHT_RED_DOMINATOR_MARKER);
        assertEquals(" &4&k|||&r",
                BloodstonePlaceholderRegistrar.DARK_RED_DOMINATOR_MARKER);
    }

    @Test
    void dominatorMarkerUsesViewerThenTargetRelation() {
        UUID dominatedPlayerId = UUID.randomUUID();
        UUID dominatorId = UUID.randomUUID();

        String marker = BloodstonePlaceholderRegistrar.dominatorMarker(
                (viewerId, targetId) ->
                        viewerId.equals(dominatedPlayerId)
                                && targetId.equals(dominatorId),
                dominatedPlayerId,
                dominatorId
        );
        assertTrue(
                marker.equals(BloodstonePlaceholderRegistrar.LIGHT_RED_DOMINATOR_MARKER)
                        || marker.equals(BloodstonePlaceholderRegistrar.DARK_RED_DOMINATOR_MARKER)
        );
        assertEquals(
                "",
                BloodstonePlaceholderRegistrar.dominatorMarker(
                        (viewerId, targetId) ->
                                viewerId.equals(dominatedPlayerId)
                                        && targetId.equals(dominatorId),
                        dominatorId,
                        dominatedPlayerId
                )
        );
    }
}
