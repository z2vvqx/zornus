package net.valoury.discord.proxy.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.discord.api.link.AccountLinkService;
import org.jspecify.annotations.NonNull;

public final class DiscordCommand {
    public static @NonNull BrigadierCommand create(AccountLinkService accountLinkService) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand
                .literalArgumentBuilder("discord")
                .requires(source -> source instanceof Player)
                .executes(DiscordHelpCommand.defaultExecutor())
                .then(DiscordHelpCommand.create())
                .then(DiscordLinkCommand.create(accountLinkService))
                .then(DiscordUnlinkCommand.create(accountLinkService))
                .build();

        return new BrigadierCommand(node);
    }
}
