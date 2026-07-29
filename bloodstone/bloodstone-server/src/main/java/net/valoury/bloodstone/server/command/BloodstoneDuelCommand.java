package net.valoury.bloodstone.server.command;

import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.service.BloodstoneDuelService;
import net.valoury.bloodstone.server.service.BloodstoneMessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BloodstoneDuelCommand implements TabExecutor {

    private final BloodstoneDuelService duelService;
    private final BloodstoneMessageService messageService;

    public BloodstoneDuelCommand(
            BloodstoneDuelService duelService,
            BloodstoneMessageService messageService
    ) {
        this.duelService = duelService;
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
        if (arguments.length != 1) {
            BloodstoneText.sendMessage(player, BloodstoneServerConstants.DUEL_USAGE);
            return true;
        }

        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> duelService.accept(player);
            case "reject" -> duelService.reject(player);
            default -> challenge(player, arguments[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (!(sender instanceof Player player) || arguments.length != 1) {
            return List.of();
        }

        String prefix = arguments[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        addIfMatching(suggestions, "accept", prefix);
        addIfMatching(suggestions, "reject", prefix);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                addIfMatching(suggestions, onlinePlayer.getName(), prefix);
            }
        }
        suggestions.sort(String.CASE_INSENSITIVE_ORDER);
        return suggestions;
    }

    private void challenge(Player challenger, String challengedPlayerName) {
        Player challengedPlayer = Bukkit.getPlayerExact(challengedPlayerName);
        if (challengedPlayer == null) {
            messageService.sendUnable(
                    challenger,
                    BloodstoneServerConstants.DUEL_TARGET_OFFLINE
            );
            return;
        }
        duelService.challenge(challenger, challengedPlayer);
    }

    private void addIfMatching(List<String> suggestions, String suggestion, String prefix) {
        if (suggestion.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            suggestions.add(suggestion);
        }
    }
}
