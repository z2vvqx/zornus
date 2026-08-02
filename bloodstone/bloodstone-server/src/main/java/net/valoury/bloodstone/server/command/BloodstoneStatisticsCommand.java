package net.valoury.bloodstone.server.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.service.BloodstoneMessageService;
import net.valoury.bloodstone.server.service.BloodstonePlayerService;
import net.valoury.shared.SharedConstants;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public final class BloodstoneStatisticsCommand implements CommandExecutor {

    private final BloodstonePlayerService playerService;
    private final BloodstoneMessageService messageService;

    public BloodstoneStatisticsCommand(
            BloodstonePlayerService playerService,
            BloodstoneMessageService messageService
    ) {
        this.playerService = playerService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (!(sender instanceof Player player)) {
            BloodstoneText.sendMessage(
                    sender,
                    BloodstoneServerConstants.COMMAND_PLAYER_ONLY
            );
            return true;
        }

        PlayerProfile profile = playerService.profile(player.getUniqueId())
                .orElse(null);
        if (profile == null) {
            messageService.sendUnable(
                    player,
                    BloodstoneServerConstants.PLAYER_DATA_UNAVAILABLE
            );
            return true;
        }

        BloodstoneText.sendMessage(player, createStatisticsDisplay(profile));
        return true;
    }

    static @NonNull Component createStatisticsDisplay(
            @NonNull PlayerProfile profile
    ) {
        TextComponent.Builder statistics = Component.text().appendNewline();
        appendStatistic(statistics, "Kills", profile.kills());
        appendStatistic(statistics, "Deaths", profile.deaths());
        appendStatistic(
                statistics,
                "Ratio",
                String.format(Locale.US, "%.2f", profile.ratio())
        );
        appendStatistic(statistics, "Assists", profile.assists());
        appendStatistic(statistics, "Carries", profile.carries());
        appendStatistic(
                statistics,
                "Rampage",
                profile.currentRampage() + "/" + profile.bestRampage()
        );
        appendStatistic(statistics, "Dominations", profile.dominations());
        appendStatistic(statistics, "Revenges", profile.revenges());
        return statistics.build();
    }

    private static void appendStatistic(
            TextComponent.Builder statistics,
            String name,
            int value
    ) {
        appendStatistic(statistics, name, Integer.toString(value));
    }

    private static void appendStatistic(
            TextComponent.Builder statistics,
            String name,
            String value
    ) {
        statistics
                .append(BloodstoneText.deserialize(
                        SharedConstants.BULLET_POINT
                                + BloodstoneServerConstants.STATISTICS_ENTRY_FORMAT,
                        Placeholder.unparsed("name", name),
                        Placeholder.unparsed("value", value)
                ))
                .appendNewline();
    }
}
