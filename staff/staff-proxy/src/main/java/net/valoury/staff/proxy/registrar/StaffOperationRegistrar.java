package net.valoury.staff.proxy.registrar;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import net.valoury.staff.proxy.StaffProxyConstants;
import net.valoury.staff.proxy.operation.StaffConnectionCleanupOperation;
import net.valoury.staff.proxy.service.StaffService;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class StaffOperationRegistrar {
    private final @NonNull Object plugin;
    private final @NonNull StaffService staffService;
    private final @NonNull List<ScheduledTask> scheduledTasks = new ArrayList<>();

    public StaffOperationRegistrar(
            @NonNull Object plugin,
            @NonNull StaffService staffService
    ) {
        this.plugin = plugin;
        this.staffService = staffService;
    }

    public void registerOperations(@NonNull Scheduler scheduler) {
        ScheduledTask cleanupTask = scheduler
                .buildTask(plugin, new StaffConnectionCleanupOperation(staffService))
                .delay(StaffProxyConstants.CLEANUP_INTERVAL)
                .repeat(StaffProxyConstants.CLEANUP_INTERVAL)
                .schedule();
        scheduledTasks.add(cleanupTask);
    }

    public void cancelOperations() {
        scheduledTasks.forEach(ScheduledTask::cancel);
        scheduledTasks.clear();
    }
}
