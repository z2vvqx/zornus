package com.zornus.guilds.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.guilds.proxy.GuildProxyConstants;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.HelpUtils;
import com.zornus.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;

public final class GuildHelpCommand {

    public static LiteralArgumentBuilder<CommandSource> create() {
        return BrigadierCommand
                .literalArgumentBuilder("help")
                .executes(context -> handleGuildHelp(context, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> handleGuildHelp(
                                context, IntegerArgumentType.getInteger(context, "page")))
                );
    }

    public static @NonNull Command<CommandSource> defaultExecutor() {
        return context -> handleGuildHelp(context, 1);
    }

    private static int handleGuildHelp(@NonNull CommandContext<CommandSource> context, int page) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        HelpUtils.sendHelpPage(sender, GuildProxyConstants.HELP_COMMANDS, page,
                GuildProxyConstants.UI_HELP_PAGINATION);
        return Command.SINGLE_SUCCESS;
    }
}
