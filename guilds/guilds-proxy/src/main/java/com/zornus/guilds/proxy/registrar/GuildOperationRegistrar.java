package com.zornus.guilds.proxy.registrar;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.guilds.proxy.operation.GuildExpirationOperation;
import com.zornus.guilds.proxy.service.GuildService;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class GuildOperationRegistrar {

    private final @NonNull Object plugin;
    private final @NonNull GuildService service;
    private final @NonNull List<ScheduledTask> scheduledTasks;

    public GuildOperationRegistrar(@NonNull Object plugin, @NonNull GuildService service) {
        this.plugin = plugin;
        this.service = service;
        this.scheduledTasks = new ArrayList<>();
    }

    public void registerOperations(@NonNull Scheduler scheduler) {
        registerGuildExpiration(scheduler);
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
