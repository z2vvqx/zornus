package net.valoury.bloodstone.server;

import net.valoury.bloodstone.server.registrar.BloodstoneCommandRegistrar;
import net.valoury.bloodstone.server.registrar.BloodstoneListenerRegistrar;
import net.valoury.bloodstone.server.registrar.BloodstoneOperationRegistrar;
import net.valoury.bloodstone.server.registrar.BloodstonePlaceholderRegistrar;
import net.valoury.bloodstone.server.registrar.BloodstoneEffectAxePacketRegistrar;
import net.valoury.bloodstone.server.registrar.BloodstoneWorldGuardRegistrar;
import net.valoury.bloodstone.server.service.BloodstoneCombatService;
import net.valoury.bloodstone.server.service.BloodstoneAxeFuserService;
import net.valoury.bloodstone.server.service.BloodstoneDuelService;
import net.valoury.bloodstone.server.service.BloodstoneEnchanterService;
import net.valoury.bloodstone.server.service.BloodstoneItemService;
import net.valoury.bloodstone.server.service.BloodstoneGuildProfileCache;
import net.valoury.bloodstone.server.service.BloodstoneLeaderboardService;
import net.valoury.bloodstone.server.service.BloodstoneMachineService;
import net.valoury.bloodstone.server.service.BloodstoneMainThreadExecutor;
import net.valoury.bloodstone.server.service.BloodstoneMessageService;
import net.valoury.bloodstone.server.service.BloodstoneMenuService;
import net.valoury.bloodstone.server.service.BloodstonePlayerNameService;
import net.valoury.bloodstone.server.service.BloodstonePlayerService;
import net.valoury.bloodstone.server.service.BloodstonePresentationService;
import net.valoury.bloodstone.server.service.BloodstoneService;
import net.valoury.bloodstone.server.service.BloodstoneSpawnProtectionService;
import net.valoury.bloodstone.server.service.BloodstoneStorageService;
import net.valoury.bloodstone.server.storage.BloodstonePostgresStorage;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import net.valoury.guilds.api.GuildsApi;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class BloodstoneServerModule {

    private final Plugin plugin;
    private final BloodstoneStorage storage;
    private final BloodstoneItemService itemService;
    private final BloodstoneSpawnProtectionService spawnProtectionService;
    private final BloodstoneCombatService combatService;
    private final BloodstoneDuelService duelService;
    private final BloodstonePlayerService playerService;
    private final BloodstoneService bloodstoneService;
    private final BloodstoneStorageService storageService;
    private final BloodstoneEnchanterService enchanterService;
    private final BloodstoneAxeFuserService axeFuserService;
    private final BloodstoneMachineService machineService;
    private final BloodstoneLeaderboardService leaderboardService;
    private final BloodstoneCommandRegistrar commandRegistrar;
    private final BloodstoneListenerRegistrar listenerRegistrar;
    private final BloodstoneOperationRegistrar operationRegistrar;
    private final @Nullable BloodstonePlaceholderRegistrar placeholderRegistrar;
    private final BloodstoneEffectAxePacketRegistrar effectAxePacketRegistrar;
    private final BloodstoneWorldGuardRegistrar worldGuardRegistrar;
    private final BloodstoneGuildProfileCache guildProfileCache;
    private final BloodstoneMessageService messageService;
    private final boolean placeholderApiAvailable;

    public BloodstoneServerModule(Plugin plugin) {
        this.plugin = plugin;
        GuildsApi guildsApi = Objects.requireNonNull(
                plugin.getServer().getServicesManager().load(GuildsApi.class),
                "GuildsServer did not register GuildsApi"
        );
        this.storage = new BloodstonePostgresStorage(
                BloodstoneServerConstants.POSTGRESQL_URL,
                BloodstoneServerConstants.POSTGRESQL_USER,
                BloodstoneServerConstants.POSTGRESQL_PASSWORD
        );
        try {
            BloodstoneMainThreadExecutor mainThreadExecutor =
                    new BloodstoneMainThreadExecutor(plugin);
            this.messageService = new BloodstoneMessageService();
            this.itemService = new BloodstoneItemService();
            BloodstonePresentationService presentationService =
                    new BloodstonePresentationService();
            this.spawnProtectionService = new BloodstoneSpawnProtectionService(plugin);
            this.playerService = new BloodstonePlayerService(
                storage,
                itemService,
                mainThreadExecutor,
                presentationService,
                messageService,
                plugin.getLogger()
            );
            BloodstoneMenuService menuService = new BloodstoneMenuService(
                itemService,
                presentationService,
                messageService
            );
            this.combatService = new BloodstoneCombatService(
                plugin,
                storage,
                guildsApi.memberships(),
                itemService,
                this.spawnProtectionService,
                presentationService,
                mainThreadExecutor,
                playerService,
                plugin.getLogger()
            );
            this.duelService = new BloodstoneDuelService(
                    plugin,
                    combatService,
                    playerService,
                    messageService
            );
            this.storageService = new BloodstoneStorageService(
                storage,
                itemService,
                playerService,
                mainThreadExecutor,
                presentationService,
                messageService,
                plugin.getLogger()
            );
            this.enchanterService = new BloodstoneEnchanterService(
                plugin,
                storage,
                itemService,
                combatService,
                playerService,
                mainThreadExecutor,
                presentationService,
                messageService,
                plugin.getLogger()
            );
            this.axeFuserService = new BloodstoneAxeFuserService(
                    plugin,
                    storage,
                    itemService,
                    combatService,
                    playerService,
                    mainThreadExecutor,
                    presentationService,
                    messageService,
                    plugin.getLogger()
            );
            this.machineService = new BloodstoneMachineService(
                plugin,
                storage,
                itemService,
                combatService,
                menuService,
                storageService,
                enchanterService,
                axeFuserService,
                playerService,
                presentationService,
                mainThreadExecutor,
                messageService,
                plugin.getLogger()
            );
            this.bloodstoneService = new BloodstoneService(
                itemService,
                storage,
                playerService,
                mainThreadExecutor,
                plugin.getLogger()
            );
            LuckPerms luckPerms = plugin.getServer()
                    .getServicesManager()
                    .load(LuckPerms.class);
            BloodstonePlayerNameService playerNameService =
                    new BloodstonePlayerNameService(
                            luckPerms,
                            plugin.getLogger()
                    );
            this.leaderboardService = new BloodstoneLeaderboardService(
                storage,
                guildsApi.memberships(),
                playerNameService
            );
            this.guildProfileCache = new BloodstoneGuildProfileCache(
                    guildsApi.memberships(),
                    plugin.getLogger()
            );
            this.effectAxePacketRegistrar = new BloodstoneEffectAxePacketRegistrar(
                    combatService,
                    mainThreadExecutor
            );
            this.worldGuardRegistrar = new BloodstoneWorldGuardRegistrar(combatService);
            this.commandRegistrar = new BloodstoneCommandRegistrar(
                    plugin,
                    menuService,
                    machineService,
                    duelService,
                    messageService
            );
            this.listenerRegistrar = new BloodstoneListenerRegistrar(
                    plugin,
                    bloodstoneService,
                    combatService,
                    duelService,
                    itemService,
                playerService,
                storageService,
                menuService,
                enchanterService,
                axeFuserService,
                machineService,
                mainThreadExecutor,
                guildProfileCache,
                effectAxePacketRegistrar,
                messageService,
                playerNameService
            );
            this.operationRegistrar = new BloodstoneOperationRegistrar(
                plugin,
                combatService,
                storageService,
                guildProfileCache,
                this.leaderboardService,
                mainThreadExecutor,
                BloodstoneServerConstants.LEADERBOARD_REFRESH_SECONDS
            );
            this.placeholderApiAvailable =
                    plugin.getServer().getPluginManager().isPluginEnabled(
                            "PlaceholderAPI"
                    );
            this.placeholderRegistrar = placeholderApiAvailable
                    ? new BloodstonePlaceholderRegistrar(
                        this.leaderboardService,
                        playerService,
                        guildProfileCache
                )
                    : null;
        } catch (RuntimeException | Error exception) {
            storage.close();
            throw exception;
        }
    }

    public CompletableFuture<Void> initialize() {
        return storage.initialize();
    }

    public CompletableFuture<Void> register(PluginManager pluginManager) {
        itemService.validateRuntime();
        spawnProtectionService.validateRuntime();
        commandRegistrar.registerCommands();
        listenerRegistrar.registerListeners(pluginManager);
        effectAxePacketRegistrar.register();
        worldGuardRegistrar.register();
        operationRegistrar.registerOperations();
        if (placeholderRegistrar != null) {
            placeholderRegistrar.register();
        }
        List<CompletableFuture<Void>> recoveries = new ArrayList<>();
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            recoveries.add(playerService.handleJoin(player));
            recoveries.add(guildProfileCache.refresh(player.getUniqueId()));
        }
        return CompletableFuture.allOf(recoveries.toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> leaderboardService.refresh())
                .thenApply(ignored -> null);
    }

    public void shutdown() {
        operationRegistrar.cancelOperations();
        effectAxePacketRegistrar.unregister();
        worldGuardRegistrar.unregister();
        if (placeholderRegistrar != null) {
            placeholderRegistrar.unregister();
        }
        machineService.shutdown();
        enchanterService.shutdown();
        axeFuserService.shutdown();
        duelService.shutdown();
        plugin.getServer().getScheduler().cancelTasks(plugin);
        combatService.shutdown();
        guildProfileCache.clear();
        messageService.clear();
        try {
            CompletableFuture.allOf(
                            bloodstoneService.shutdown(),
                            storageService.shutdown(),
                            playerService.shutdown()
                    )
                    .get(BloodstoneServerConstants.SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to flush all Bloodstone state before database shutdown", exception);
        } finally {
            storageService.stopRetries();
            storage.close();
        }
    }
}
