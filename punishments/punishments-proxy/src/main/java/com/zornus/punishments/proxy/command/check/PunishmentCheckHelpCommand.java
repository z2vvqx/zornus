package com.zornus.punishments.proxy.command.check;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.zornus.punishments.proxy.PunishmentProxyConstants;
import com.zornus.shared.utilities.HelpUtils;
import org.jspecify.annotations.NonNull;

public final class PunishmentCheckHelpCommand {

    public static LiteralArgumentBuilder<CommandSource> create() {
        return BrigadierCommand
                .literalArgumentBuilder("help")
                .executes(context -> handleDisplayHelp(context, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> handleDisplayHelp(context, IntegerArgumentType.getInteger(context, "page")))
                );
    }

    public static @NonNull Command<CommandSource> defaultExecutor() {
        return context -> handleDisplayHelp(context, 1);
    }

    private static int handleDisplayHelp(@NonNull CommandContext<CommandSource> context, int page) {
        HelpUtils.sendHelpPage(context.getSource(), PunishmentProxyConstants.HELP_COMMANDS_CHECK, page, PunishmentProxyConstants.UI_HELP_PAGINATION_CHECK);
        return Command.SINGLE_SUCCESS;
    }
}
