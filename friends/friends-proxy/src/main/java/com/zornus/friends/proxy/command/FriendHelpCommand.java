package com.zornus.friends.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.utilities.HelpUtils;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

/**
 * Command for displaying friend system help.
 */
public final class FriendHelpCommand {

    public static LiteralArgumentBuilder<CommandSource> create(FriendService friendService) {
        return BrigadierCommand
                .literalArgumentBuilder("help")
                .executes(context -> handleDisplayHelp(context, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int page = IntegerArgumentType.getInteger(context, "page");
                            return handleDisplayHelp(context, page);
                        })
                );
    }

    @Contract(pure = true)
    public static @NonNull Command<CommandSource> defaultExecutor(FriendService friendService) {
        return context -> handleDisplayHelp(context, 1);
    }

    private static int handleDisplayHelp(@NonNull CommandContext<CommandSource> context, int page) {
        HelpUtils.sendHelpPage(context.getSource(), FriendProxyConstants.HELP_COMMANDS, page, FriendProxyConstants.UI_HELP_PAGINATION);
        return Command.SINGLE_SUCCESS;
    }
}
