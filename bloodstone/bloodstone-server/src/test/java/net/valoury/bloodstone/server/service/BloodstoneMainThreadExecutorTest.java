package net.valoury.bloodstone.server.service;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BloodstoneMainThreadExecutorTest {

    @Test
    void rejectsWithoutSchedulingWhenPluginIsDisabled() {
        BloodstoneMainThreadExecutor executor =
                new BloodstoneMainThreadExecutor(disabledPlugin());
        AtomicBoolean executed = new AtomicBoolean();

        assertFalse(executor.executeIfEnabled(() -> executed.set(true)));
        assertFalse(executed.get());
        assertThrows(
                RejectedExecutionException.class,
                () -> executor.execute(() -> executed.set(true))
        );
        assertFalse(executed.get());
    }

    private static Plugin disabledPlugin() {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("isEnabled")) {
                        return false;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
