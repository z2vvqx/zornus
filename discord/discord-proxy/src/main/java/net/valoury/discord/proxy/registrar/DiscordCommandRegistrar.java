package net.valoury.discord.proxy.registrar;

import com.velocitypowered.api.command.CommandManager;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.proxy.command.DiscordCommand;
import net.valoury.discord.proxy.command.DiscordLinkCommand;
import net.valoury.discord.proxy.command.DiscordUnlinkCommand;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class DiscordCommandRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordCommandRegistrar.class);

    private final @NonNull AccountLinkService accountLinkService;

    public DiscordCommandRegistrar(@NonNull AccountLinkService accountLinkService) {
        this.accountLinkService = Objects.requireNonNull(
                accountLinkService,
                "Account link service cannot be null"
        );
    }

    public void registerCommands(@NonNull CommandManager commandManager) {
        Objects.requireNonNull(commandManager, "Command manager cannot be null");
        try {
            registerDiscordCommand(commandManager);
        } catch (RuntimeException exception) {
            LOGGER.error("Error registering Discord commands", exception);
            throw exception;
        }
    }

    private void registerDiscordCommand(@NonNull CommandManager commandManager) {
        commandManager.register(
                commandManager.metaBuilder("discord").build(),
                DiscordCommand.create(accountLinkService)
        );
        commandManager.register(
                commandManager.metaBuilder("link").build(),
                DiscordLinkCommand.createShortcut(accountLinkService)
        );
        commandManager.register(
                commandManager.metaBuilder("unlink").build(),
                DiscordUnlinkCommand.createShortcut(accountLinkService)
        );
    }
}
