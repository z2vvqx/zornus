package com.zornus.parties.proxy.registrar;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.storage.PartyStorage;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PartyOperationRegistrar {

    private final @NonNull Object plugin;
    private final @NonNull PartyStorage storage;
    private final @NonNull List<ScheduledTask> scheduledTasks;

    public PartyOperationRegistrar(@NonNull Object plugin, @NonNull PartyStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.scheduledTasks = new ArrayList<>();
    }

    public void registerOperations(@NonNull Scheduler scheduler) {
        scheduledTasks.add(scheduler.buildTask(plugin, () -> storage.cleanupExpiredInvitations(Instant.now(), PartyProxyConstants.INVITATION_EXPIRY))
                .delay(1, TimeUnit.MINUTES)
                .repeat(1, TimeUnit.MINUTES)
                .schedule());

        scheduledTasks.add(scheduler.buildTask(plugin, () -> storage.cleanupExpiredConfirmations(Instant.now(), PartyProxyConstants.CONFIRMATION_EXPIRY))
                .delay(1, TimeUnit.MINUTES)
                .repeat(1, TimeUnit.MINUTES)
                .schedule());

        scheduledTasks.add(scheduler.buildTask(plugin, () -> storage.cleanupExpiredCooldowns(Instant.now(), PartyProxyConstants.INVITATION_COOLDOWN.multipliedBy(2)))
                .delay(5, TimeUnit.MINUTES)
                .repeat(5, TimeUnit.MINUTES)
                .schedule());

        scheduledTasks.add(scheduler.buildTask(plugin, () -> storage.cleanupOrphanedSettings())
                .delay(10, TimeUnit.MINUTES)
                .repeat(10, TimeUnit.MINUTES)
                .schedule());
    }

    public void cancelOperations() {
        for (ScheduledTask task : scheduledTasks) {
            task.cancel();
        }
        scheduledTasks.clear();
    }
}
