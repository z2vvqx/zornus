package net.valoury.bloodstone.server.command;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.service.BloodstoneMachineService;
import net.valoury.bloodstone.server.service.BloodstoneMenuService;
import net.valoury.bloodstone.server.service.BloodstoneMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BloodstoneDisenchantCommand implements CommandExecutor {

    private final BloodstoneMenuService menuService;
    private final BloodstoneMachineService machineService;
    private final BloodstoneMessageService messageService;

    public BloodstoneDisenchantCommand(
            BloodstoneMenuService menuService,
            BloodstoneMachineService machineService,
            BloodstoneMessageService messageService
    ) {
        this.menuService = menuService;
        this.machineService = machineService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            BloodstoneText.sendMessage(
                    sender,
                    BloodstoneServerConstants.COMMAND_PLAYER_ONLY
            );
            return true;
        }
        if (!menuService.isInBloodstone(player)) {
            messageService.sendError(
                    player,
                    BloodstoneServerConstants.COMMAND_BLOODSTONE_ONLY
            );
            return true;
        }
        if (machineService.isUnavailable(player.getUniqueId())) {
            messageService.sendUnable(
                    player,
                    BloodstoneServerConstants.PLAYER_DATA_UNAVAILABLE
            );
            return true;
        }
        menuService.disenchantHeldItem(player, arguments);
        return true;
    }
}
