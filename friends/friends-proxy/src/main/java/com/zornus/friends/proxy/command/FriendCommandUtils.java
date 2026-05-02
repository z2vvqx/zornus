package com.zornus.friends.proxy.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.friends.proxy.service.FriendService;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Utility methods for friend commands.
 */
public final class FriendCommandUtils {

    private FriendCommandUtils() {
    }

    /**
     * Resolves a target player by username. First checks online players,
     * then falls back to the database for offline players.
     *
     * @param username     the username to resolve
     * @param proxyServer  the proxy server instance
     * @param friendService the friend service for database lookups
     * @return a CompletableFuture containing the optional UUID of the player
     */
    public static CompletableFuture<Optional<UUID>> resolveTargetPlayer(
            String username,
            @NonNull ProxyServer proxyServer,
            FriendService friendService) {
        Optional<Player> onlinePlayer = proxyServer.getPlayer(username);
        if (onlinePlayer.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(onlinePlayer.get().getUniqueId()));
        }
        return friendService.fetchPlayerByUsername(username)
                .thenApply(optional -> optional.map(playerRecord -> playerRecord.playerUuid()));
    }
}
