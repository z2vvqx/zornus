package com.zornus.punishments.proxy;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.punishments.proxy.registrar.PunishmentCommandRegistrar;
import com.zornus.punishments.proxy.registrar.PunishmentListenerRegistrar;
import com.zornus.punishments.proxy.registrar.PunishmentOperationRegistrar;
import com.zornus.punishments.proxy.service.PunishmentService;
import com.zornus.punishments.proxy.storage.PunishmentPostgresStorage;
import com.zornus.punishments.proxy.storage.PunishmentStorage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PunishmentProxyModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentProxyModule.class);

    private final @NonNull PunishmentService punishmentService;
    private final @NonNull PunishmentCommandRegistrar punishmentCommandRegistrar;
    private final @NonNull PunishmentListenerRegistrar punishmentListenerRegistrar;
    private final @NonNull PunishmentOperationRegistrar punishmentOperationRegistrar;

    public PunishmentProxyModule(@NonNull Object plugin, @NonNull ProxyServer proxyServer) {
        PunishmentStorage storage = new PunishmentPostgresStorage(
                PunishmentProxyConstants.POSTGRESQL_URL,
                PunishmentProxyConstants.POSTGRESQL_USER,
                PunishmentProxyConstants.POSTGRESQL_PASSWORD);
        this.punishmentService = new PunishmentService(storage, proxyServer);
        this.punishmentCommandRegistrar = new PunishmentCommandRegistrar(punishmentService, proxyServer);
        this.punishmentListenerRegistrar = new PunishmentListenerRegistrar(plugin, punishmentService);
        this.punishmentOperationRegistrar = new PunishmentOperationRegistrar(plugin, punishmentService);
    }

    public void initialize(@NonNull CommandManager commandManager,
                           @NonNull EventManager eventManager,
                           @NonNull Scheduler scheduler) {
        try {
            punishmentCommandRegistrar.registerCommands(commandManager);
            punishmentListenerRegistrar.registerListeners(eventManager);
            punishmentOperationRegistrar.registerOperations(scheduler);
        } catch (Exception exception) {
            LOGGER.error("Failed to initialize punishment proxy module", exception);
            throw new RuntimeException("Failed to initialize punishment proxy module", exception);
        }
    }

    public void shutdown() {
        try {
            punishmentOperationRegistrar.cancelOperations();
            punishmentService.close();
        } catch (Exception exception) {
            LOGGER.error("Error during punishment proxy module shutdown", exception);
        }
    }

    public @NonNull PunishmentService getPunishmentService() {
        return punishmentService;
    }

    public @NonNull PunishmentCommandRegistrar getCommandRegistrar() {
        return punishmentCommandRegistrar;
    }

    public @NonNull PunishmentListenerRegistrar getListenerRegistrar() {
        return punishmentListenerRegistrar;
    }

    public @NonNull PunishmentOperationRegistrar getOperationRegistrar() {
        return punishmentOperationRegistrar;
    }
}
