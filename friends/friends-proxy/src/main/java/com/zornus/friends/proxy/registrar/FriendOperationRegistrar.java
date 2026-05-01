package com.zornus.friends.proxy.registrar;

import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.operation.FriendExpirationOperation;
import com.zornus.friends.proxy.storage.FriendStorage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Registrar for friend scheduled operations.
 */
public class FriendOperationRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendOperationRegistrar.class);

    private final @NonNull Object plugin;
    private final @NonNull FriendExpirationOperation expirationOperation;

    /**
     * Creates a new operation registrar.
     *
     * @param plugin  The plugin instance for task registration
     * @param storage Storage for friend operations
     */
    public FriendOperationRegistrar(@NonNull Object plugin, @NonNull FriendStorage storage) {
        this.plugin = plugin;
        this.expirationOperation = new FriendExpirationOperation(storage);
    }

    /**
     * Registers all friend operations.
     * This operation is thread-safe and includes proper error handling.
     *
     * @param scheduler The scheduler for task registration
     */
    public void registerOperations(@NonNull Scheduler scheduler) {
        try {
            registerExpiryOperation(scheduler);
        } catch (Exception exception) {
            LOGGER.error("Error registering friend operations", exception);
            throw exception;
        }
    }

    /**
     * Registers the expiry operation with periodic scheduling.
     *
     * @param scheduler The scheduler for task registration
     */
    private void registerExpiryOperation(@NonNull Scheduler scheduler) {
        Duration cleanupInterval = FriendProxyConstants.CLEANUP_TASK_INTERVAL;

        scheduler.buildTask(plugin, expirationOperation)
                .repeat(cleanupInterval.toMillis(), TimeUnit.MILLISECONDS)
                .schedule();
    }

    /**
     * Gets the expiry operation.
     *
     * @return Expiry operation
     */
    public @NonNull FriendExpirationOperation getExpirationOperation() {
        return expirationOperation;
    }
}
