package net.valoury.staff.proxy.operation;

import net.valoury.staff.proxy.service.StaffService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StaffConnectionCleanupOperation implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            StaffConnectionCleanupOperation.class
    );

    private final @NonNull StaffService staffService;

    public StaffConnectionCleanupOperation(@NonNull StaffService staffService) {
        this.staffService = staffService;
    }

    @Override
    public void run() {
        staffService.cleanupExpiredConnections()
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to clean expired staff connections", throwable);
                    return null;
                });
    }
}
