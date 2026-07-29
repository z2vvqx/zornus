package net.valoury.bloodstone.server.service;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DuelSessionRegistryTest {

    @Test
    void requestIsIndexedAndRemovedForBothPlayers() {
        DuelSessionRegistry sessions = new DuelSessionRegistry();
        UUID challengerId = UUID.randomUUID();
        UUID challengedPlayerId = UUID.randomUUID();

        DuelRequest request = sessions.createRequest(challengerId, challengedPlayerId);

        assertSame(request, sessions.requestFor(challengerId));
        assertSame(request, sessions.requestFor(challengedPlayerId));
        assertTrue(sessions.isBusy(challengerId));
        assertTrue(sessions.isBusy(challengedPlayerId));

        sessions.remove(request);

        assertNull(sessions.requestFor(challengerId));
        assertNull(sessions.requestFor(challengedPlayerId));
        assertFalse(sessions.isBusy(challengerId));
        assertFalse(sessions.isBusy(challengedPlayerId));
    }

    @Test
    void duelTransitionsAndEndsForBothPlayers() {
        DuelSessionRegistry sessions = new DuelSessionRegistry();
        UUID challengerId = UUID.randomUUID();
        UUID challengedPlayerId = UUID.randomUUID();
        World world = world();
        DuelPosition sideA = new DuelPosition(world, 1.0D, 2.0D, 3.0D, 0.0F, 0.0F);
        DuelPosition sideB = new DuelPosition(world, 4.0D, 5.0D, 6.0D, 180.0F, 0.0F);

        ActiveDuel countdown = sessions.createDuel(
                challengerId,
                challengedPlayerId,
                sideA,
                sideB
        );
        ActiveDuel active = sessions.activate(countdown);

        assertEquals(DuelPhase.ACTIVE, active.phase());
        assertSame(active, sessions.duelFor(challengerId));
        assertSame(active, sessions.duelFor(challengedPlayerId));
        assertSame(active, sessions.removeDuelFor(challengerId));
        assertNull(sessions.duelFor(challengerId));
        assertNull(sessions.duelFor(challengedPlayerId));
    }

    @Test
    void participantLookupRejectsUnknownPlayers() {
        UUID challengerId = UUID.randomUUID();
        UUID challengedPlayerId = UUID.randomUUID();
        DuelRequest request = new DuelRequest(
                UUID.randomUUID(),
                challengerId,
                challengedPlayerId
        );

        assertEquals(challengedPlayerId, request.opponentOf(challengerId));
        assertEquals(challengerId, request.opponentOf(challengedPlayerId));
        assertThrows(
                IllegalArgumentException.class,
                () -> request.opponentOf(UUID.randomUUID())
        );
    }

    private World world() {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> null
        );
    }
}
