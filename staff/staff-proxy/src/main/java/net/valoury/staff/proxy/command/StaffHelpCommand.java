package net.valoury.staff.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.valoury.shared.utilities.HelpUtils;
import net.valoury.staff.proxy.StaffProxyConstants;
import org.jspecify.annotations.NonNull;

public final class StaffHelpCommand {
    public static @NonNull LiteralArgumentBuilder<CommandSource> create() {
        return BrigadierCommand
                .literalArgumentBuilder("help")
                .executes(context -> displayHelp(context, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> displayHelp(
                                context,
                                IntegerArgumentType.getInteger(context, "page")
                        ))
                );
    }

    public static @NonNull Command<CommandSource> defaultExecutor() {
        return context -> displayHelp(context, 1);
    }

    private static int displayHelp(
            @NonNull CommandContext<CommandSource> context,
            int page
    ) {
        HelpUtils.sendHelpPage(
                context.getSource(),
                StaffProxyConstants.HELP_COMMANDS,
                page,
                StaffProxyConstants.UI_HELP_PAGINATION
        );
        return Command.SINGLE_SUCCESS;
    }
}
