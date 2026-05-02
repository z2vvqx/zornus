package com.zornus.parties.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.zornus.parties.proxy.PartyProxyConstants;
import com.zornus.parties.proxy.service.PartyService;
import com.zornus.shared.SharedConstants;
import com.zornus.shared.utilities.HelpUtils;
import com.zornus.shared.utilities.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

/**
 * Command for displaying party help.
 */
public final class PartyHelpCommand {

    public static LiteralArgumentBuilder<CommandSource> create(PartyService partyService) {
        return BrigadierCommand
                .literalArgumentBuilder("help")
                .executes(context -> handlePartyHelp(context, 1))
                .then(BrigadierCommand
                        .requiredArgumentBuilder("page", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int page = IntegerArgumentType.getInteger(context, "page");
                            return handlePartyHelp(context, page);
                        })
                );
    }

    @Contract(pure = true)
    public static @NonNull Command<CommandSource> defaultExecutor(PartyService partyService) {
        return context -> handlePartyHelp(context, 1);
    }

    private static int handlePartyHelp(@NonNull CommandContext<CommandSource> context, int page) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player sender)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        HelpUtils.sendHelpPage(sender, PartyProxyConstants.HELP_COMMANDS, page, PartyProxyConstants.UI_HELP_PAGINATION);
        return Command.SINGLE_SUCCESS;
    }
}
