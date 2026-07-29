package net.valoury.guilds.server.storage;

import net.valoury.guilds.api.GuildProfile;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildServerStorage extends AutoCloseable {

    @NonNull CompletableFuture<Void> validateSchema();

    @NonNull CompletableFuture<Optional<GuildProfile>> findGuildByPlayer(@NonNull UUID playerId);

    @NonNull CompletableFuture<Optional<GuildProfile>> findGuild(@NonNull UUID guildId);

    @Override
    void close();
}
