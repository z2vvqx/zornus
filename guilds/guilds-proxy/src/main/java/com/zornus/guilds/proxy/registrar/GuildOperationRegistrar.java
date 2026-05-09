package com.zornus.guilds.proxy.registrar;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.operation.GuildExpirationOperation;
import com.zornus.guilds.proxy.service.GuildService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class GuildOperationRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildOperationRegistrar.class);

    private final @NonNull Object plugin;
    private final @NonNull GuildService service;
    private final @NonNull List<ScheduledTask> scheduledTasks;

    public GuildOperationRegistrar(@NonNull Object plugin, @NonNull GuildService service) {
        this.plugin = plugin;
        this.service = service;
        this.scheduledTasks = new ArrayList<>();
    }

    public void registerOperations(@NonNull Scheduler scheduler) {
        try {
            registerGuildExpiration(scheduler);
        } catch (Exception exception) {
            LOGGER.error("Error registering guild operations", exception);
            throw exception;
        }
    }

    private void registerGuildExpiration(@NonNull Scheduler scheduler) {
        ScheduledTask task = scheduler.buildTask(plugin, new GuildExpirationOperation(service))
                .delay(GuildProxyConstants.CLEANUP_TASK_INTERVAL)
                .repeat(GuildProxyConstants.CLEANUP_TASK_INTERVAL)
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
