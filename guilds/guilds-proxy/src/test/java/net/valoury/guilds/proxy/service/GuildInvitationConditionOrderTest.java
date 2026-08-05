package net.valoury.guilds.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.valoury.friends.api.FriendshipService;
import net.valoury.guilds.proxy.model.Guild;
import net.valoury.guilds.proxy.model.GuildRank;
import net.valoury.guilds.proxy.model.result.GuildResults;
import net.valoury.guilds.proxy.storage.GuildStorage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GuildInvitationConditionOrderTest {

    @Test
    void rejectsSenderWithoutGuildBeforeResolvingTarget() {
        UUID senderId = UUID.randomUUID();
        GuildStorage storage = storageReturning(Optional.empty());
        GuildService service = createService(storage, failingProxy(ProxyServer.class));

        GuildResults.SendInvitation result = service.sendInvitation(
                player(senderId, "Sender"),
                "MissingTarget"
        ).join();

        assertInstanceOf(GuildResults.SendInvitation.NotInGuild.class, result);
    }

    @Test
    void rejectsInsufficientRankBeforeResolvingTarget() {
        UUID leaderId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Guild guild = new Guild(
                UUID.randomUUID(),
                "Guild",
                "TAG",
                "white",
                leaderId,
                Instant.now(),
                Map.of(leaderId, GuildRank.LEADER, senderId, GuildRank.OUTCAST)
        );
        GuildService service = createService(
                storageReturning(Optional.of(guild)),
                failingProxy(ProxyServer.class)
        );

        GuildResults.SendInvitation result = service.sendInvitation(
                player(senderId, "Sender"),
                "MissingTarget"
        ).join();

        assertInstanceOf(GuildResults.SendInvitation.InsufficientRank.class, result);
    }

    private static GuildService createService(GuildStorage storage, ProxyServer proxyServer) {
        FriendshipService friendshipService = (firstPlayerId, secondPlayerId) -> {
            throw new AssertionError("Friendship lookup must not run before sender authorization");
        };
        return new GuildService(
                storage,
                proxyServer,
                friendshipService,
                failingProxy(LuckPerms.class)
        );
    }

    private static GuildStorage storageReturning(Optional<Guild> guild) {
        return proxy(GuildStorage.class, (instance, method, arguments) -> {
            if (method.getName().equals("getPlayerGuild")) {
                return CompletableFuture.completedFuture(guild);
            }
            throw new AssertionError("Unexpected storage call before sender authorization: "
                    + method.getName());
        });
    }

    private static Player player(UUID playerId, String username) {
        return proxy(Player.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> playerId;
            case "getUsername" -> username;
            default -> throw new AssertionError("Unexpected player call: " + method.getName());
        });
    }

    private static <T> T failingProxy(Class<T> type) {
        return proxy(type, (instance, method, arguments) -> {
            throw new AssertionError("Unexpected " + type.getSimpleName() + " call: "
                    + method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
