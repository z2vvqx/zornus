package com.zornus.parties.proxy.registrar;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.operation.PartyExpirationOperation;
import com.zornus.parties.proxy.service.PartyService;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

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
        registerPartyExpiration(scheduler);
    }

    private void registerPartyExpiration(@NonNull Scheduler scheduler) {
        ScheduledTask task = scheduler.buildTask(plugin, new PartyExpirationOperation(service))
                .delay(PartyProxyConstants.CLEANUP_TASK_INTERVAL)
                .repeat(PartyProxyConstants.CLEANUP_TASK_INTERVAL)
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
