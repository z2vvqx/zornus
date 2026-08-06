package net.valoury.staff.proxy.registrar;

import com.velocitypowered.api.event.EventManager;
import net.valoury.staff.proxy.listener.player.StaffConnectionListener;
import net.valoury.staff.proxy.service.StaffService;
import org.jspecify.annotations.NonNull;

public final class StaffListenerRegistrar {
    private final @NonNull Object plugin;
    private final @NonNull StaffService staffService;

    public StaffListenerRegistrar(
            @NonNull Object plugin,
            @NonNull StaffService staffService
    ) {
        this.plugin = plugin;
        this.staffService = staffService;
    }

    public void registerListeners(@NonNull EventManager eventManager) {
        eventManager.register(plugin, new StaffConnectionListener(staffService));
    }
}
