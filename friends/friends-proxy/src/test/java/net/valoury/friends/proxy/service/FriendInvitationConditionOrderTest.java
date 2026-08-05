package net.valoury.friends.proxy.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.valoury.friends.proxy.storage.FriendStorage;
import net.valoury.shared.model.PlayerRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FriendInvitationConditionOrderTest {

    @Test
    void resolvesSelfWithoutProxyOrDatabaseLookup() {
        UUID senderId = UUID.randomUUID();
        Player sender = player(senderId, "Sender");
        FriendService service = new FriendService(
                failingProxy(FriendStorage.class),
                failingProxy(ProxyServer.class),
                failingProxy(LuckPerms.class)
        );

        Optional<PlayerRecord> resolvedPlayer = service.resolveTargetPlayer(sender, "sEnDeR").join();

        assertEquals(Optional.of(new PlayerRecord(senderId, "Sender")), resolvedPlayer);
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
