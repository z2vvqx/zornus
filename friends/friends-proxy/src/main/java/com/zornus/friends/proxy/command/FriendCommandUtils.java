package com.zornus.friends.proxy.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zornus.friends.proxy.service.FriendService;
import com.zornus.shared.model.PlayerRecord;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Utility methods for friend commands.
 */
public final class FriendCommandUtils {

    private FriendCommandUtils() {
    }

    /**
     * Resolves a target player by username. First checks online players (a case-insensitive
     * lookup per {@link ProxyServer#getPlayer(String)}), then falls back to the database for
     * offline players (also case-insensitive; see {@link FriendService#fetchPlayerByUsername}).
     * <p>
     * The returned {@link PlayerRecord} carries the player's actual stored username, not the
     * input string, so callers displaying the name back to a player (e.g. "Friend request sent
     * to &lt;target&gt;!") show the correct casing regardless of how the command's caller typed it.
     *
     * @param username      the username to resolve
     * @param proxyServer   the proxy server instance
     * @param friendService the friend service for database lookups
     * @return a CompletableFuture containing the resolved player's UUID and correctly-cased username, if found
     */
    public static CompletableFuture<Optional<PlayerRecord>> resolveTargetPlayer(
            String username,
            @NonNull ProxyServer proxyServer,
            FriendService friendService) {
        Optional<Player> onlinePlayer = proxyServer.getPlayer(username);
        if (onlinePlayer.isPresent()) {
            Player player = onlinePlayer.get();
            return CompletableFuture.completedFuture(
                    Optional.of(new PlayerRecord(player.getUniqueId(), player.getUsername())));
        }
        return friendService.fetchPlayerByUsername(username);
    }
}
