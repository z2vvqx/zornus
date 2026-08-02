package net.valoury.discord.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.discord.proxy.DiscordProxyConstants;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.HelpUtils;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;

public final class DiscordHelpCommand {
    public static LiteralArgumentBuilder<CommandSource> create() {
        return BrigadierCommand
                .literalArgumentBuilder("help")
                .executes(context -> handleDisplayHelp(context, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> handleDisplayHelp(
                                context,
                                IntegerArgumentType.getInteger(context, "page")
                        ))
                );
    }

    public static @NonNull Command<CommandSource> defaultExecutor() {
        return context -> handleDisplayHelp(context, 1);
    }

    private static int handleDisplayHelp(@NonNull CommandContext<CommandSource> context, int page) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        HelpUtils.sendHelpPage(
                player,
                DiscordProxyConstants.HELP_COMMANDS,
                page,
                DiscordProxyConstants.UI_HELP_PAGINATION
        );
        return Command.SINGLE_SUCCESS;
    }
}
