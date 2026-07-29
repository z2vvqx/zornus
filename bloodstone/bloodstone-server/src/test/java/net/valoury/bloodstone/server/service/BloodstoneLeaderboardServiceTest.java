package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.model.GuildLeaderboardEntry;
import net.valoury.bloodstone.server.model.LeaderboardBoard;
import net.valoury.bloodstone.server.model.LeaderboardMetric;
import net.valoury.bloodstone.server.model.LeaderboardSnapshot;
import net.valoury.bloodstone.server.model.PlayerLeaderboardEntry;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.api.GuildProfile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneLeaderboardServiceTest {

    @Test
    void formatsAllBoardsAndRetainsTheLastSnapshotWhenRefreshFails() {
        AtomicBoolean failPlayerBest = new AtomicBoolean();
        UUID playerId = new UUID(0L, 1L);
        UUID guildId = new UUID(0L, 2L);
        BloodstoneStorage storage = (BloodstoneStorage) Proxy.newProxyInstance(
                BloodstoneStorage.class.getClassLoader(),
                new Class<?>[]{BloodstoneStorage.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "fetchPlayerLeaderboard" -> {
                        if (failPlayerBest.get()
                                && arguments[0] == LeaderboardMetric.BEST_RAMPAGE) {
                            yield CompletableFuture.failedFuture(
                                    new IllegalStateException("intentional failure"));
                        }
                        yield CompletableFuture.completedFuture(List.of(
                                new PlayerLeaderboardEntry(playerId, "PlayerOne", 7)
                        ));
                    }
                    case "fetchGuildLeaderboard" -> CompletableFuture.completedFuture(List.of(
                            new GuildLeaderboardEntry(guildId, 9)
                    ));
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        GuildMembershipService memberships = new GuildMembershipService() {
            @Override
            public CompletableFuture<Optional<GuildProfile>> findGuildByPlayer(UUID ignored) {
                return CompletableFuture.completedFuture(Optional.of(
                        new GuildProfile(guildId, "Guild One", "ONE", "GOLD")
                ));
            }

            @Override
            public CompletableFuture<Optional<GuildProfile>> findGuild(UUID ignored) {
                return CompletableFuture.completedFuture(Optional.of(
                        new GuildProfile(guildId, "Guild One", "ONE", "GOLD")
                ));
            }
        };
        BloodstoneLeaderboardService service =
                new BloodstoneLeaderboardService(storage, memberships);

        LeaderboardSnapshot complete = service.refresh().join();
        assertEquals(LeaderboardBoard.values().length, complete.entries().size());
        assertEquals(1, complete.entries().get(LeaderboardBoard.PLAYER_KILLS).size());
        assertEquals(1, complete.entries().get(LeaderboardBoard.GUILD_KILLS).size());
        String playerEntry = complete.entries()
                .get(LeaderboardBoard.PLAYER_KILLS)
                .getFirst();
        String guildEntry = complete.entries()
                .get(LeaderboardBoard.GUILD_KILLS)
                .getFirst();
        assertTrue(playerEntry.contains("PlayerOne"));
        assertTrue(guildEntry.contains("Guild One [ONE]"));
        assertFalse(playerEntry.contains("<"));
        assertFalse(guildEntry.contains("<"));
        assertTrue(playerEntry.contains("§a⚔"));
        assertTrue(guildEntry.contains("§a⚔"));
        assertTrue(complete.entries()
                .get(LeaderboardBoard.PLAYER_CURRENT_RAMPAGE)
                .getFirst()
                .contains("§6➹"));
        assertTrue(complete.entries()
                .get(LeaderboardBoard.PLAYER_BEST_RAMPAGE)
                .getFirst()
                .contains("§6➹"));
        assertTrue(complete.entries()
                .get(LeaderboardBoard.GUILD_CURRENT_RAMPAGE)
                .getFirst()
                .contains("§6➹"));
        assertTrue(complete.entries()
                .get(LeaderboardBoard.GUILD_BEST_RAMPAGE)
                .getFirst()
                .contains("§6➹"));
        assertTrue(service.entry(LeaderboardBoard.PLAYER_KILLS, 0)
                .contains("§a⚔"));
        assertTrue(service.entry(LeaderboardBoard.PLAYER_CURRENT_RAMPAGE, 0)
                .contains("§6➹"));
        assertFalse(service.entry(LeaderboardBoard.PLAYER_KILLS, 0).contains("<"));

        failPlayerBest.set(true);
        assertThrows(CompletionException.class, () -> service.refresh().join());
        assertSame(complete, service.snapshot());
    }
}
