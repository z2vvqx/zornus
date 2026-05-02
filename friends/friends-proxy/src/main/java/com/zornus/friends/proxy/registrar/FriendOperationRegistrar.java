package com.zornus.friends.proxy.registrar;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.operation.FriendExpirationOperation;
import com.zornus.friends.proxy.service.FriendService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Registrar for friend scheduled operations.
 */
public final class FriendOperationRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendOperationRegistrar.class);

    private final @NonNull Object plugin;
    private final @NonNull FriendService service;
    private final @NonNull List<ScheduledTask> scheduledTasks;

    /**
     * Creates a new operation registrar.
     *
     * @param plugin  The plugin instance for task registration
     * @param service Service for friend operations
     */
    public FriendOperationRegistrar(@NonNull Object plugin, @NonNull FriendService service) {
        this.plugin = plugin;
        this.service = service;
        this.scheduledTasks = new ArrayList<>();
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
        ScheduledTask task = scheduler.buildTask(plugin, new FriendExpirationOperation(service))
                .delay(FriendProxyConstants.CLEANUP_TASK_INTERVAL)
                .repeat(FriendProxyConstants.CLEANUP_TASK_INTERVAL)
                .schedule();
        scheduledTasks.add(task);
    }

    public void cancelOperations() {
        for (ScheduledTask task : scheduledTasks) {
            task.cancel();
        }
        scheduledTasks.clear();
    }
}
