package net.valoury.staff.proxy.registrar;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.staff.proxy.command.StaffCommand;
import net.valoury.staff.proxy.service.StaffService;
import org.jspecify.annotations.NonNull;

public final class StaffCommandRegistrar {
    private final @NonNull StaffService staffService;
    private final @NonNull ProxyServer proxyServer;

    public StaffCommandRegistrar(
            @NonNull StaffService staffService,
            @NonNull ProxyServer proxyServer
    ) {
        this.staffService = staffService;
        this.proxyServer = proxyServer;
    }

    public void registerCommands(@NonNull CommandManager commandManager) {
        commandManager.register(
                commandManager.metaBuilder("staff").build(),
                StaffCommand.create(staffService, proxyServer)
        );
    }
}
