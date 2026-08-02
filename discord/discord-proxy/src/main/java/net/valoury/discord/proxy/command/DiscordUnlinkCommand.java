package net.valoury.discord.proxy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.api.link.UnlinkAccountResult;
import net.valoury.discord.proxy.DiscordProxyConstants;
import net.valoury.shared.SharedConstants;
import net.valoury.shared.utilities.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiscordUnlinkCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordUnlinkCommand.class);

    public static LiteralArgumentBuilder<CommandSource> create(AccountLinkService accountLinkService) {
        return createCommand("unlink", accountLinkService);
    }

    public static @NonNull BrigadierCommand createShortcut(AccountLinkService accountLinkService) {
        return new BrigadierCommand(createCommand("unlink", accountLinkService)
                .requires(source -> source instanceof Player));
    }

    private static LiteralArgumentBuilder<CommandSource> createCommand(
            String commandName,
            AccountLinkService accountLinkService
    ) {
        return BrigadierCommand
                .literalArgumentBuilder(commandName)
                .executes(context -> handleUnlinkAccount(context, accountLinkService));
    }

    private static int handleUnlinkAccount(
            @NonNull CommandContext<CommandSource> context,
            AccountLinkService accountLinkService
    ) {
        CommandSource source = context.getSource();
        if (!(source instanceof Player player)) {
            source.sendMessage(StringUtils.deserialize(SharedConstants.PLAYERS_ONLY));
            return Command.SINGLE_SUCCESS;
        }

        accountLinkService.unlinkByMinecraftUniqueId(player.getUniqueId())
                .thenAccept(result -> {
                    String feedback = switch (result) {
                        case UnlinkAccountResult.Unlinked ignored -> DiscordProxyConstants.UNLINK_SUCCESS;
                        case UnlinkAccountResult.NotLinked ignored -> DiscordProxyConstants.UNLINK_NOT_LINKED;
                    };
                    player.sendMessage(StringUtils.deserialize(feedback));
                })
                .exceptionally(throwable -> {
                    LOGGER.error(
                            "Failed to unlink the Discord account for player {}",
                            player.getUniqueId(),
                            throwable
                    );
                    player.sendMessage(StringUtils.deserialize(SharedConstants.ERROR_UNEXPECTED));
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }
}
