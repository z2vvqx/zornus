package net.valoury.friends.proxy.registrar;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.valoury.friends.proxy.command.FriendCommand;
import net.valoury.friends.proxy.command.FriendListCommand;
import net.valoury.friends.proxy.command.FriendMessageCommand;
import net.valoury.friends.proxy.command.FriendReplyCommand;
import net.valoury.friends.proxy.service.FriendService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registrar for friend commands.
 */
public final class FriendCommandRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendCommandRegistrar.class);

    private final @NonNull FriendService friendService;
    private final @NonNull ProxyServer proxyServer;

    /**
     * Creates a new command registrar.
     *
     * @param friendService Service for friend operations
     * @param proxyServer   Proxy server for player lookups
     */
    public FriendCommandRegistrar(@NonNull FriendService friendService, @NonNull ProxyServer proxyServer) {
        this.friendService = friendService;
        this.proxyServer = proxyServer;
    }

    /**
     * Registers all friend commands.
     * This operation is thread-safe and includes proper error handling.
     *
     * @param commandManager The command manager for command registration
     */
    public void registerCommands(@NonNull CommandManager commandManager) {
        try {
            registerFriendCommand(commandManager);
        } catch (Exception exception) {
            LOGGER.error("Error registering friend commands", exception);
            throw exception;
        }
    }

    /**
     * Registers the main friend command with all subcommands.
     *
     * @param commandManager The command manager for command registration
     */
    private void registerFriendCommand(@NonNull CommandManager commandManager) {
        commandManager.register(commandManager.metaBuilder("friend").aliases("f").build(), FriendCommand.create(friendService, proxyServer));
        commandManager.register(FriendListCommand.createShortcut(friendService, proxyServer));
        commandManager.register(
                commandManager.metaBuilder("reply").aliases("r").build(),
                new BrigadierCommand(FriendReplyCommand.create(friendService)
                        .requires(source -> source instanceof Player))
        );
        commandManager.register(
                commandManager.metaBuilder("message").aliases("m", "w", "whisper", "tell", "msg").build(),
                new BrigadierCommand(FriendMessageCommand.create(friendService, proxyServer)
                        .requires(source -> source instanceof Player))
        );
    }
}
