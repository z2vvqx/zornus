package net.valoury.discord.proxy;

import com.velocitypowered.api.command.CommandManager;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.internal.InternalConstants;
import net.valoury.discord.internal.storage.DiscordPostgresStorage;
import net.valoury.discord.proxy.registrar.DiscordCommandRegistrar;

public final class DiscordProxyModule implements AutoCloseable {
    private final DiscordPostgresStorage storage;
    private final AccountLinkService accountLinkService;
    private final DiscordCommandRegistrar discordCommandRegistrar;

    public DiscordProxyModule() {
        this.storage = new DiscordPostgresStorage(
                InternalConstants.POSTGRESQL_URL,
                InternalConstants.POSTGRESQL_USER,
                InternalConstants.POSTGRESQL_PASSWORD
        );
        try {
            this.accountLinkService = new AccountLinkService(storage);
            this.discordCommandRegistrar = new DiscordCommandRegistrar(accountLinkService);
        } catch (RuntimeException exception) {
            storage.close();
            throw exception;
        }
    }

    public void initialize(CommandManager commandManager) {
        discordCommandRegistrar.registerCommands(commandManager);
    }

    public AccountLinkService accountLinks() {
        return accountLinkService;
    }

    @Override
    public void close() {
        storage.close();
    }
}
