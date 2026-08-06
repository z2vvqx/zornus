package net.valoury.discord.proxy;

import com.velocitypowered.api.command.CommandManager;
import net.valoury.discord.api.evidence.EvidenceService;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.internal.InternalConstants;
import net.valoury.discord.internal.storage.DiscordPostgresStorage;
import net.valoury.discord.internal.storage.EvidencePostgresStorage;
import net.valoury.discord.proxy.registrar.DiscordCommandRegistrar;

public final class DiscordProxyModule implements AutoCloseable {
    private final DiscordPostgresStorage storage;
    private final EvidencePostgresStorage evidenceStorage;
    private final AccountLinkService accountLinkService;
    private final EvidenceService evidenceService;
    private final DiscordCommandRegistrar discordCommandRegistrar;

    public DiscordProxyModule() {
        this.storage = new DiscordPostgresStorage(
                InternalConstants.POSTGRESQL_URL,
                InternalConstants.POSTGRESQL_USER,
                InternalConstants.POSTGRESQL_PASSWORD
        );
        EvidencePostgresStorage initializedEvidenceStorage = null;
        try {
            initializedEvidenceStorage = new EvidencePostgresStorage(
                    InternalConstants.POSTGRESQL_URL,
                    InternalConstants.POSTGRESQL_USER,
                    InternalConstants.POSTGRESQL_PASSWORD
            );
            this.evidenceStorage = initializedEvidenceStorage;
            this.accountLinkService = new AccountLinkService(storage);
            this.evidenceService = new EvidenceService(evidenceStorage);
            this.discordCommandRegistrar = new DiscordCommandRegistrar(accountLinkService);
        } catch (RuntimeException exception) {
            if (initializedEvidenceStorage != null) {
                try {
                    initializedEvidenceStorage.close();
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            try {
                storage.close();
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public void initialize(CommandManager commandManager) {
        discordCommandRegistrar.registerCommands(commandManager);
    }

    public AccountLinkService accountLinks() {
        return accountLinkService;
    }

    public EvidenceService evidence() {
        return evidenceService;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        try {
            evidenceStorage.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            storage.close();
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
