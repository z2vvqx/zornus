package net.valoury.bloodstone.server.registrar;

import net.valoury.bloodstone.server.listener.BloodstoneInteractionListener;
import net.valoury.bloodstone.server.listener.inventory.BloodstoneInventoryListener;
import net.valoury.bloodstone.server.listener.player.BloodstoneChatListener;
import net.valoury.bloodstone.server.listener.player.BloodstoneCombatListener;
import net.valoury.bloodstone.server.listener.player.BloodstoneConnectionListener;
import net.valoury.bloodstone.server.listener.player.BloodstoneDeathAndRespawnListener;
import net.valoury.bloodstone.server.listener.player.BloodstoneDuelListener;
import net.valoury.bloodstone.server.service.BloodstoneCombatService;
import net.valoury.bloodstone.server.service.BloodstoneAxeFuserService;
import net.valoury.bloodstone.server.service.BloodstoneDuelService;
import net.valoury.bloodstone.server.service.BloodstoneEffectAxeService;
import net.valoury.bloodstone.server.service.BloodstoneEnchanterService;
import net.valoury.bloodstone.server.service.BloodstoneGuildProfileCache;
import net.valoury.bloodstone.server.service.BloodstoneItemService;
import net.valoury.bloodstone.server.service.BloodstoneMachineService;
import net.valoury.bloodstone.server.service.BloodstoneMainThreadExecutor;
import net.valoury.bloodstone.server.service.BloodstoneMessageService;
import net.valoury.bloodstone.server.service.BloodstoneMenuService;
import net.valoury.bloodstone.server.service.BloodstonePlayerNameService;
import net.valoury.bloodstone.server.service.BloodstonePlayerService;
import net.valoury.bloodstone.server.service.BloodstoneService;
import net.valoury.bloodstone.server.service.BloodstoneStorageService;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class BloodstoneListenerRegistrar {

    private final Plugin plugin;
    private final BloodstoneService bloodstoneService;
    private final BloodstoneCombatService combatService;
    private final BloodstoneDuelService duelService;
    private final BloodstoneItemService itemService;
    private final BloodstoneEffectAxeService effectAxeService;
    private final BloodstonePlayerService playerService;
    private final BloodstoneStorageService storageService;
    private final BloodstoneMenuService menuService;
    private final BloodstoneEnchanterService enchanterService;
    private final BloodstoneAxeFuserService axeFuserService;
    private final BloodstoneMachineService machineService;
    private final BloodstoneMainThreadExecutor mainThreadExecutor;
    private final BloodstoneGuildProfileCache guildProfileCache;
    private final BloodstoneEffectAxePacketRegistrar effectAxePacketRegistrar;
    private final BloodstoneMessageService messageService;
    private final BloodstonePlayerNameService playerNameService;

    public BloodstoneListenerRegistrar(
            Plugin plugin,
            BloodstoneService bloodstoneService,
            BloodstoneCombatService combatService,
            BloodstoneDuelService duelService,
            BloodstoneItemService itemService,
            BloodstoneEffectAxeService effectAxeService,
            BloodstonePlayerService playerService,
            BloodstoneStorageService storageService,
            BloodstoneMenuService menuService,
            BloodstoneEnchanterService enchanterService,
            BloodstoneAxeFuserService axeFuserService,
            BloodstoneMachineService machineService,
            BloodstoneMainThreadExecutor mainThreadExecutor,
            BloodstoneGuildProfileCache guildProfileCache,
            BloodstoneEffectAxePacketRegistrar effectAxePacketRegistrar,
            BloodstoneMessageService messageService,
            BloodstonePlayerNameService playerNameService
    ) {
        this.plugin = plugin;
        this.bloodstoneService = bloodstoneService;
        this.combatService = combatService;
        this.duelService = duelService;
        this.itemService = itemService;
        this.effectAxeService = effectAxeService;
        this.playerService = playerService;
        this.storageService = storageService;
        this.menuService = menuService;
        this.enchanterService = enchanterService;
        this.axeFuserService = axeFuserService;
        this.machineService = machineService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.guildProfileCache = guildProfileCache;
        this.effectAxePacketRegistrar = effectAxePacketRegistrar;
        this.messageService = messageService;
        this.playerNameService = playerNameService;
    }

    public void registerListeners(PluginManager pluginManager) {
        pluginManager.registerEvents(
                new BloodstoneDeathAndRespawnListener(
                        bloodstoneService,
                        combatService,
                        duelService,
                        plugin
                ),
                plugin
        );
        pluginManager.registerEvents(new BloodstoneDuelListener(duelService), plugin);
        pluginManager.registerEvents(
                new BloodstoneCombatListener(combatService, effectAxeService),
                plugin
        );
        pluginManager.registerEvents(
                new BloodstoneConnectionListener(
                        playerService,
                        combatService,
                        duelService,
                        storageService,
                        enchanterService,
                        axeFuserService,
                        guildProfileCache,
                        effectAxePacketRegistrar,
                        messageService
                ),
                plugin
        );
        pluginManager.registerEvents(
                new BloodstoneChatListener(
                        playerService,
                        mainThreadExecutor,
                        messageService,
                        playerNameService
                ),
                plugin
        );
        pluginManager.registerEvents(new BloodstoneInteractionListener(machineService), plugin);
        pluginManager.registerEvents(
                new BloodstoneInventoryListener(
                        menuService,
                        storageService,
                        enchanterService,
                        axeFuserService,
                        itemService
                ),
                plugin
        );
    }
}
