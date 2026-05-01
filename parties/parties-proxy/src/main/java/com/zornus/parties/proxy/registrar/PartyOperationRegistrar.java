package com.zornus.parties.proxy.registrar;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.parties.proxy.operation.PartyExpirationOperation;
import com.zornus.parties.proxy.service.PartyService;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PartyOperationRegistrar {

    private final @NonNull Object plugin;
    private final @NonNull PartyService service;
    private final @NonNull List<ScheduledTask> scheduledTasks;

    public PartyOperationRegistrar(@NonNull Object plugin, @NonNull PartyService service) {
        this.plugin = plugin;
        this.service = service;
        this.scheduledTasks = new ArrayList<>();
    }

    public void registerOperations(@NonNull Scheduler scheduler) {
        scheduledTasks.add(scheduler.buildTask(plugin, new PartyExpirationOperation(service))
                .delay(1, TimeUnit.MINUTES)
                .repeat(1, TimeUnit.MINUTES)
                .schedule());
    }

    public void cancelOperations() {
        for (ScheduledTask task : scheduledTasks) {
            task.cancel();
        }
        scheduledTasks.clear();
    }
}
