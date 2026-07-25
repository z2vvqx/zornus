package net.valoury.punishments.proxy.registrar;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import net.valoury.punishments.proxy.PunishmentProxyConstants;
import net.valoury.punishments.proxy.operation.PunishmentExpirationOperation;
import net.valoury.punishments.proxy.service.PunishmentService;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class PunishmentOperationRegistrar {
    private final @NonNull Object plugin;
    private final @NonNull PunishmentService punishmentService;
    private final @NonNull List<ScheduledTask> scheduledTasks = new ArrayList<>();

    public PunishmentOperationRegistrar(
            @NonNull Object plugin,
            @NonNull PunishmentService punishmentService
    ) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
    }

    public void registerOperations(@NonNull Scheduler scheduler) {
        ScheduledTask expirationTask = scheduler
                .buildTask(plugin, new PunishmentExpirationOperation(punishmentService))
                .delay(PunishmentProxyConstants.CLEANUP_INTERVAL)
                .repeat(PunishmentProxyConstants.CLEANUP_INTERVAL)
                .schedule();
        scheduledTasks.add(expirationTask);
    }

    public void cancelOperations() {
        scheduledTasks.forEach(ScheduledTask::cancel);
        scheduledTasks.clear();
    }
}
