package net.valoury.bloodstone.server.registrar;

import net.valoury.bloodstone.server.command.BloodstoneDuelCommand;
import net.valoury.bloodstone.server.command.BloodstoneMenuCommand;
import net.valoury.bloodstone.server.command.BloodstoneStackCommand;
import net.valoury.bloodstone.server.command.BloodstoneStatisticsCommand;
import net.valoury.bloodstone.server.command.BloodstoneTrashCommand;
import net.valoury.bloodstone.server.service.BloodstoneDuelService;
import net.valoury.bloodstone.server.service.BloodstoneMenuService;
import net.valoury.bloodstone.server.service.BloodstoneMachineService;
import net.valoury.bloodstone.server.service.BloodstoneMessageService;
import net.valoury.bloodstone.server.service.BloodstonePlayerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class BloodstoneCommandRegistrar {

    private final Plugin plugin;
    private final BloodstoneMenuService menuService;
    private final BloodstoneMachineService machineService;
    private final BloodstoneDuelService duelService;
    private final BloodstonePlayerService playerService;
    private final BloodstoneMessageService messageService;

    public BloodstoneCommandRegistrar(
            Plugin plugin,
            BloodstoneMenuService menuService,
            BloodstoneMachineService machineService,
            BloodstoneDuelService duelService,
            BloodstonePlayerService playerService,
            BloodstoneMessageService messageService
    ) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.machineService = machineService;
        this.duelService = duelService;
        this.playerService = playerService;
        this.messageService = messageService;
    }

    public void registerCommands() {
        requireCommand("bloodstone").setExecutor(
                new BloodstoneMenuCommand(menuService, machineService, messageService));
        requireCommand("trash").setExecutor(
                new BloodstoneTrashCommand(menuService, machineService, messageService));
        requireCommand("stack").setExecutor(
                new BloodstoneStackCommand(menuService, machineService, messageService));
        requireCommand("duel").setExecutor(
                new BloodstoneDuelCommand(duelService, messageService));
        requireCommand("statistics").setExecutor(
                new BloodstoneStatisticsCommand(playerService, messageService));
    }

    private PluginCommand requireCommand(String name) {
        return Objects.requireNonNull(plugin.getServer().getPluginCommand(name),
                "Command is missing from plugin.yml: " + name);
    }
}
