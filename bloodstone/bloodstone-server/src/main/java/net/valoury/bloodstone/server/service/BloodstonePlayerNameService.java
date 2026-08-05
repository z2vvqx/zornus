package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.valoury.bloodstone.server.BloodstonePlayerIdentity;
import net.valoury.bloodstone.server.BloodstoneText;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class BloodstonePlayerNameService {

    private final @Nullable LuckPerms luckPerms;
    private final Logger logger;

    public BloodstonePlayerNameService(
            @Nullable LuckPerms luckPerms,
            Logger logger
    ) {
        this.luckPerms = luckPerms;
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
    }

    public Component resolveOnlinePlayerName(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        Component displayName = player.displayName();
        if (luckPerms == null) {
            return displayName;
        }
        try {
            String suffix = luckPerms.getPlayerAdapter(Player.class)
                    .getMetaData(player)
                    .getSuffix();
            return formatPlayerName(suffix, displayName);
        } catch (RuntimeException exception) {
            logger.warning(
                    "Failed to resolve LuckPerms suffix for "
                            + player.getUniqueId()
                            + "; using display name without a suffix"
            );
            return displayName;
        }
    }

    public Component resolvePlayerName(
            @Nullable Player player,
            UUID playerId
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        return player == null
                ? Component.text(playerId.toString().substring(0, 8))
                : resolveOnlinePlayerName(player);
    }

    public CompletableFuture<Component> resolveStoredPlayerName(
            UUID playerId,
            String username
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Component displayName = Component.text(username);
        if (luckPerms == null
                || !BloodstonePlayerIdentity.isValidUsername(username)) {
            return CompletableFuture.completedFuture(displayName);
        }
        try {
            return luckPerms.getUserManager()
                    .loadUser(playerId, username)
                    .thenApply(user -> formatPlayerName(
                            user.getCachedData().getMetaData().getSuffix(),
                            displayName
                    ))
                    .exceptionally(exception -> {
                        logStoredNameFallback(playerId);
                        return displayName;
                    });
        } catch (RuntimeException exception) {
            logStoredNameFallback(playerId);
            return CompletableFuture.completedFuture(displayName);
        }
    }

    static Component formatPlayerName(
            @Nullable String suffix,
            Component displayName
    ) {
        Objects.requireNonNull(displayName, "Player display name cannot be null");
        return suffix == null || suffix.isEmpty()
                ? displayName
                : BloodstoneText.ampersandComponent(
                        suffix + BloodstoneText.ampersand(displayName)
                );
    }

    private void logStoredNameFallback(UUID playerId) {
        logger.warning(
                "Failed to resolve LuckPerms suffix for "
                        + playerId
                        + "; using stored username without a suffix"
        );
    }
}
