package com.zornus.friends.proxy.registrar;

import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.storage.FriendStorage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Registrar for friend scheduled operations.
 */
public final class FriendOperationRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendOperationRegistrar.class);

    private final @NonNull Object plugin;
    private final @NonNull FriendStorage storage;

    /**
     * Creates a new operation registrar.
     *
     * @param plugin  The plugin instance for task registration
     * @param storage Storage for friend operations
     */
    public FriendOperationRegistrar(@NonNull Object plugin, @NonNull FriendStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
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

        scheduler.buildTask(plugin, this::runCleanup)
                .repeat(cleanupInterval.toMillis(), TimeUnit.MILLISECONDS)
                .schedule();
    }

    /**
     * Runs the cleanup of expired friend requests, cooldowns, and last message senders.
     */
    private void runCleanup() {
        try {
            LOGGER.debug("Starting cleanup of expired friend requests, cooldowns, and last message senders...");

            Instant now = Instant.now();
            storage.cleanupExpiredFriendRequests(now, FriendProxyConstants.REQUEST_EXPIRY_DURATION);
            storage.cleanupExpiredFriendRequestCooldowns(now, FriendProxyConstants.COOLDOWN_EXPIRY_DURATION);
            storage.cleanupExpiredLastMessageSenders(now, FriendProxyConstants.LAST_MESSAGE_SENDER_RETENTION);

            LOGGER.debug("Completed cleanup of expired friend requests, cooldowns, and last message senders");
        } catch (Exception exception) {
            LOGGER.error("Error during friend cleanup operation: {}", exception.getMessage(), exception);
        }
    }
}
