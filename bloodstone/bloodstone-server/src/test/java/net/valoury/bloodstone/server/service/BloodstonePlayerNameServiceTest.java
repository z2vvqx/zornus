package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.UserManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BloodstonePlayerNameServiceTest {

    private static final UUID PLAYER_ID =
            UUID.fromString("e7161f11-1536-4fcc-9639-937461677ee0");

    @Test
    void invalidStoredUsernameDoesNotReachLuckPerms() {
        AtomicInteger loadAttempts = new AtomicInteger();
        UserManager userManager = proxy(
                UserManager.class,
                (proxy, method, arguments) -> {
                    if (method.getName().equals("loadUser")) {
                        loadAttempts.incrementAndGet();
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        BloodstonePlayerNameService service = service(userManager);
        String invalidUsername = "e7161f11-1536-4f";

        Component resolved = service.resolveStoredPlayerName(
                PLAYER_ID,
                invalidUsername
        ).join();

        assertEquals(Component.text(invalidUsername), resolved);
        assertEquals(0, loadAttempts.get());
    }

    @Test
    void synchronousLuckPermsFailureUsesTheStoredUsername() {
        UserManager userManager = proxy(
                UserManager.class,
                (proxy, method, arguments) -> {
                    if (method.getName().equals("loadUser")) {
                        throw new IllegalArgumentException(
                                "intentional synchronous validation failure"
                        );
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        BloodstonePlayerNameService service = service(userManager);

        Component resolved = service.resolveStoredPlayerName(
                PLAYER_ID,
                "MMAJED"
        ).join();

        assertEquals(Component.text("MMAJED"), resolved);
    }

    private static BloodstonePlayerNameService service(
            UserManager userManager
    ) {
        LuckPerms luckPerms = proxy(
                LuckPerms.class,
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getUserManager")) {
                        return userManager;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        return new BloodstonePlayerNameService(
                luckPerms,
                Logger.getAnonymousLogger()
        );
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }
}
