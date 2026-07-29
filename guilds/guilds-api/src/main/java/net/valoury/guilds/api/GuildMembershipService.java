package net.valoury.guilds.api;

import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provides read-only access to guild membership and guild display data.
 */
public interface GuildMembershipService {

    /**
     * Finds the guild containing a player.
     *
     * @param playerId player identifier
     * @return future containing the guild, or empty when the player has no guild
     */
    @NonNull CompletableFuture<Optional<GuildProfile>> findGuildByPlayer(@NonNull UUID playerId);

    /**
     * Finds a guild by its identifier.
     *
     * @param guildId guild identifier
     * @return future containing the guild, or empty when it does not exist
     */
    @NonNull CompletableFuture<Optional<GuildProfile>> findGuild(@NonNull UUID guildId);
}
