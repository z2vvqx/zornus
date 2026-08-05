package net.valoury.parties.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.valoury.friends.api.FriendshipService;
import net.valoury.parties.proxy.model.Party;
import net.valoury.parties.proxy.model.result.PartyResults;
import net.valoury.parties.proxy.storage.PartyStorage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyInvitationConditionOrderTest {

    @Test
    void rejectsMemberWithoutInvitationRoleBeforeResolvingTarget() {
        UUID leaderId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Party party = new Party(
                UUID.randomUUID(),
                leaderId,
                Set.of(leaderId, senderId),
                Set.of(),
                Optional.<Instant>empty()
        );
        PartyService service = createService(
                storageReturning(Optional.of(party)),
                failingProxy(ProxyServer.class)
        );

        PartyResults.SendInvitation result = service.sendInvitation(
                player(senderId, "Sender"),
                "MissingTarget"
        ).join();

        assertInstanceOf(PartyResults.SendInvitation.InsufficientRole.class, result);
    }

    @Test
    void resolvesTargetWhenStandaloneInvitationCanCreateParty() {
        AtomicBoolean targetLookupPerformed = new AtomicBoolean();
        ProxyServer proxyServer = proxy(ProxyServer.class, (instance, method, arguments) -> {
            if (method.getName().equals("getPlayer")
                    && arguments != null
                    && arguments.length == 1
                    && arguments[0] instanceof String) {
                targetLookupPerformed.set(true);
                return Optional.empty();
            }
            throw new AssertionError("Unexpected proxy call: " + method.getName());
        });
        PartyService service = createService(storageReturning(Optional.empty()), proxyServer);

        PartyResults.SendInvitation result = service.sendInvitation(
                player(UUID.randomUUID(), "Sender"),
                "MissingTarget"
        ).join();

        assertInstanceOf(PartyResults.SendInvitation.PlayerNotFound.class, result);
        assertTrue(targetLookupPerformed.get());
    }

    private static PartyService createService(PartyStorage storage, ProxyServer proxyServer) {
        FriendshipService friendshipService = (firstPlayerId, secondPlayerId) -> {
            throw new AssertionError("Friendship lookup must not run before target validation");
        };
        return new PartyService(
                storage,
                proxyServer,
                friendshipService,
                failingProxy(LuckPerms.class)
        );
    }

    private static PartyStorage storageReturning(Optional<Party> party) {
        return proxy(PartyStorage.class, (instance, method, arguments) -> {
            if (method.getName().equals("getPlayerParty")) {
                return CompletableFuture.completedFuture(party);
            }
            throw new AssertionError("Unexpected storage call before invitation validation: "
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
