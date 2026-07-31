package net.valoury.bloodstone.server.service;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.valoury.bloodstone.server.model.GuildLeaderboardEntry;
import net.valoury.bloodstone.server.model.LeaderboardBoard;
import net.valoury.bloodstone.server.model.LeaderboardMetric;
import net.valoury.bloodstone.server.model.LeaderboardSnapshot;
import net.valoury.bloodstone.server.model.PlayerLeaderboardEntry;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.api.GuildProfile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneLeaderboardServiceTest {

    @Test
    void formatsAllBoardsAndRetainsTheLastSnapshotWhenRefreshFails() {
        AtomicBoolean failPlayerBest = new AtomicBoolean();
        AtomicInteger playerSuffixLoads = new AtomicInteger();
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
        CachedMetaData cachedMetaData = proxy(
                CachedMetaData.class,
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getSuffix" -> "&6[VIP] &b";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        CachedDataManager cachedData = proxy(
                CachedDataManager.class,
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMetaData" -> cachedMetaData;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        User user = proxy(
                User.class,
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getCachedData" -> cachedData;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        UserManager userManager = proxy(
                UserManager.class,
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "loadUser" -> {
                        assertEquals(playerId, arguments[0]);
                        assertEquals("PlayerOne", arguments[1]);
                        playerSuffixLoads.incrementAndGet();
                        yield CompletableFuture.completedFuture(user);
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        LuckPerms luckPerms = proxy(
                LuckPerms.class,
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUserManager" -> userManager;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        BloodstoneLeaderboardService service =
                new BloodstoneLeaderboardService(
                        storage,
                        memberships,
                        new BloodstonePlayerNameService(
                                luckPerms,
                                Logger.getAnonymousLogger()
                        )
                );

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
        assertTrue(playerEntry.contains("[VIP]"));
        assertTrue(playerEntry.indexOf("[VIP]") < playerEntry.indexOf("PlayerOne"));
        assertTrue(
                playerEntry.contains("§bPlayerOne"),
                () -> playerEntry.replace('§', '&')
        );
        assertTrue(playerEntry.contains("PlayerOne"));
        assertEquals(1, playerSuffixLoads.get());
        assertTrue(guildEntry.contains("Guild One [ONE]"));
        assertFalse(playerEntry.contains("<"));
        assertFalse(guildEntry.contains("<"));
        assertTrue(playerEntry.contains("§a§l⚔§r "));
        assertTrue(guildEntry.contains("§a§l⚔§r "));
        assertTrue(complete.entries()
                .get(LeaderboardBoard.PLAYER_CURRENT_RAMPAGE)
                .getFirst()
                .contains("§b§lᐃ§r "));
        assertTrue(complete.entries()
                .get(LeaderboardBoard.PLAYER_BEST_RAMPAGE)
                .getFirst()
                .contains("§b§lᐃ§r "));
        assertTrue(complete.entries()
                .get(LeaderboardBoard.GUILD_CURRENT_RAMPAGE)
                .getFirst()
                .contains("§b§lᐃ§r "));
        assertTrue(complete.entries()
                .get(LeaderboardBoard.GUILD_BEST_RAMPAGE)
                .getFirst()
                .contains("§b§lᐃ§r "));
        assertTrue(service.entry(LeaderboardBoard.PLAYER_KILLS, 0)
                .contains("§a§l⚔§r "));
        assertTrue(service.entry(LeaderboardBoard.PLAYER_CURRENT_RAMPAGE, 0)
                .contains("§b§lᐃ§r "));
        assertFalse(service.entry(LeaderboardBoard.PLAYER_KILLS, 0).contains("<"));

        failPlayerBest.set(true);
        assertThrows(CompletionException.class, () -> service.refresh().join());
        assertSame(complete, service.snapshot());
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }
}
