package com.zornus.guilds.proxy;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.guilds.proxy.registrar.GuildCommandRegistrar;
import com.zornus.guilds.proxy.registrar.GuildListenerRegistrar;
import com.zornus.guilds.proxy.registrar.GuildOperationRegistrar;
import com.zornus.guilds.proxy.service.GuildService;
import com.zornus.guilds.proxy.storage.GuildPostgresStorage;
import com.zornus.guilds.proxy.storage.GuildStorage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildProxyModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildProxyModule.class);

    private final @NonNull GuildService guildService;
    private final @NonNull GuildCommandRegistrar guildCommandRegistrar;
    private final @NonNull GuildListenerRegistrar guildListenerRegistrar;
    private final @NonNull GuildOperationRegistrar guildOperationRegistrar;

    public GuildProxyModule(@NonNull Object plugin, @NonNull ProxyServer proxyServer) {
        this(plugin, proxyServer, null);
    }

    public GuildProxyModule(@NonNull Object plugin, @NonNull ProxyServer proxyServer, @Nullable FriendService friendService) {
        GuildStorage storage = new GuildPostgresStorage(
                GuildProxyConstants.POSTGRESQL_URL,
                GuildProxyConstants.POSTGRESQL_USER,
                GuildProxyConstants.POSTGRESQL_PASSWORD
        );
        this.guildService = new GuildService(storage, proxyServer, friendService);
        this.guildCommandRegistrar = new GuildCommandRegistrar(guildService, proxyServer);
        this.guildListenerRegistrar = new GuildListenerRegistrar(plugin, guildService);
        this.guildOperationRegistrar = new GuildOperationRegistrar(plugin, guildService);
    }

    public void initialize(@NonNull CommandManager commandManager, @NonNull EventManager eventManager, @NonNull Scheduler scheduler) {
        try {
            guildCommandRegistrar.registerCommands(commandManager);
            guildListenerRegistrar.registerListeners(eventManager);
            guildOperationRegistrar.registerOperations(scheduler);
        } catch (Exception exception) {
            LOGGER.error("Failed to initialize guild proxy module", exception);
            throw new RuntimeException("Failed to initialize guild proxy module", exception);
        }
    }

    public void shutdown() {
        try {
            guildOperationRegistrar.cancelOperations();
            guildService.close();
        } catch (Exception exception) {
            LOGGER.error("Error during guild proxy module shutdown", exception);
        }
    }

    public @NonNull GuildService getGuildService() {
        return guildService;
    }

    public @NonNull GuildCommandRegistrar getCommandRegistrar() {
        return guildCommandRegistrar;
    }

    public @NonNull GuildListenerRegistrar getListenerRegistrar() {
        return guildListenerRegistrar;
    }

    public @NonNull GuildOperationRegistrar getOperationRegistrar() {
        return guildOperationRegistrar;
    }
}
