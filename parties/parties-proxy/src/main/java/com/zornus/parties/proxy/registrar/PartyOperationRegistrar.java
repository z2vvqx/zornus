package com.zornus.parties.proxy.registrar;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.operation.PartyExpirationOperation;
import com.zornus.parties.proxy.service.PartyService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class PartyOperationRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyOperationRegistrar.class);

    private final @NonNull Object plugin;
    private final @NonNull PartyService service;
    private final @NonNull List<ScheduledTask> scheduledTasks;

    public PartyOperationRegistrar(@NonNull Object plugin, @NonNull PartyService service) {
        this.plugin = plugin;
        this.service = service;
        this.scheduledTasks = new ArrayList<>();
    }

    public void registerOperations(@NonNull Scheduler scheduler) {
        try {
            registerPartyExpiration(scheduler);
        } catch (Exception exception) {
            LOGGER.error("Error registering party operations", exception);
            throw exception;
        }
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
