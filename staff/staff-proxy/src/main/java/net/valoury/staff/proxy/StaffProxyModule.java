package net.valoury.staff.proxy;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import net.valoury.staff.proxy.registrar.StaffCommandRegistrar;
import net.valoury.staff.proxy.registrar.StaffListenerRegistrar;
import net.valoury.staff.proxy.registrar.StaffOperationRegistrar;
import net.valoury.staff.proxy.security.AddressFingerprintService;
import net.valoury.staff.proxy.service.StaffService;
import net.valoury.staff.proxy.storage.StaffPostgresStorage;
import net.valoury.staff.proxy.storage.StaffStorage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StaffProxyModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffProxyModule.class);

    private final @NonNull StaffService staffService;
    private final @NonNull StaffCommandRegistrar staffCommandRegistrar;
    private final @NonNull StaffListenerRegistrar staffListenerRegistrar;
    private final @NonNull StaffOperationRegistrar staffOperationRegistrar;

    public StaffProxyModule(
            @NonNull Object plugin,
            @NonNull ProxyServer proxyServer
    ) {
        AddressFingerprintService addressFingerprintService =
                AddressFingerprintService.fromConfiguredKey();
        StaffStorage storage = new StaffPostgresStorage(
                StaffProxyConstants.POSTGRESQL_URL,
                StaffProxyConstants.POSTGRESQL_USER,
                StaffProxyConstants.POSTGRESQL_PASSWORD
        );
        this.staffService = new StaffService(
                storage,
                proxyServer,
                addressFingerprintService
        );
        this.staffCommandRegistrar = new StaffCommandRegistrar(staffService, proxyServer);
        this.staffListenerRegistrar = new StaffListenerRegistrar(plugin, staffService);
        this.staffOperationRegistrar = new StaffOperationRegistrar(plugin, staffService);
    }

    public void initialize(
            @NonNull CommandManager commandManager,
            @NonNull EventManager eventManager,
            @NonNull Scheduler scheduler
    ) {
        try {
            staffCommandRegistrar.registerCommands(commandManager);
            staffListenerRegistrar.registerListeners(eventManager);
            staffOperationRegistrar.registerOperations(scheduler);
        } catch (Exception exception) {
            LOGGER.error("Failed to initialize staff proxy module", exception);
            throw new RuntimeException("Failed to initialize staff proxy module", exception);
        }
    }

    public void shutdown() {
        try {
            staffOperationRegistrar.cancelOperations();
            staffService.close();
        } catch (Exception exception) {
            LOGGER.error("Error during staff proxy module shutdown", exception);
        }
    }
}
