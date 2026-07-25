package net.valoury.parties.proxy;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import net.valoury.friends.api.FriendshipService;
import net.valoury.parties.proxy.registrar.PartyCommandRegistrar;
import net.valoury.parties.proxy.registrar.PartyListenerRegistrar;
import net.valoury.parties.proxy.registrar.PartyOperationRegistrar;
import net.valoury.parties.proxy.service.PartyService;
import net.valoury.parties.proxy.storage.PartyPostgresStorage;
import net.valoury.parties.proxy.storage.PartyStorage;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartyProxyModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyProxyModule.class);

    private final @NonNull PartyService partyService;
    private final @NonNull PartyCommandRegistrar partyCommandRegistrar;
    private final @NonNull PartyListenerRegistrar partyListenerRegistrar;
    private final @NonNull PartyOperationRegistrar partyOperationRegistrar;

    public PartyProxyModule(
            @NonNull Object plugin,
            @NonNull ProxyServer proxyServer,
            @NonNull FriendshipService friendshipService
    ) {
        PartyStorage storage = new PartyPostgresStorage(
                PartyProxyConstants.POSTGRESQL_URL,
                PartyProxyConstants.POSTGRESQL_USER,
                PartyProxyConstants.POSTGRESQL_PASSWORD
        );
        this.partyService = new PartyService(storage, proxyServer, friendshipService);
        this.partyCommandRegistrar = new PartyCommandRegistrar(partyService, proxyServer);
        this.partyListenerRegistrar = new PartyListenerRegistrar(plugin, partyService);
        this.partyOperationRegistrar = new PartyOperationRegistrar(plugin, partyService);
    }

    public void initialize(@NonNull CommandManager commandManager, @NonNull EventManager eventManager, @NonNull Scheduler scheduler) {
        try {
            partyCommandRegistrar.registerCommands(commandManager);
            partyListenerRegistrar.registerListeners(eventManager);
            partyOperationRegistrar.registerOperations(scheduler);
        } catch (Exception exception) {
            LOGGER.error("Failed to initialize party proxy module", exception);
            throw new RuntimeException("Failed to initialize party proxy module", exception);
        }
    }

    public void shutdown() {
        try {
            partyOperationRegistrar.cancelOperations();
            partyService.close();
        } catch (Exception exception) {
            LOGGER.error("Error during party proxy module shutdown", exception);
        }
    }

    public @NonNull PartyService getPartyService() {
        return partyService;
    }

    public @NonNull PartyCommandRegistrar getCommandRegistrar() {
        return partyCommandRegistrar;
    }

    public @NonNull PartyListenerRegistrar getListenerRegistrar() {
        return partyListenerRegistrar;
    }

    public @NonNull PartyOperationRegistrar getOperationRegistrar() {
        return partyOperationRegistrar;
    }
}
