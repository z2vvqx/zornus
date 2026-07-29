package net.valoury.guilds.server.service;

import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.api.GuildProfile;
import net.valoury.guilds.server.storage.GuildServerStorage;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GuildMembershipServiceImpl implements GuildMembershipService {

    private final GuildServerStorage storage;
    private final CompletableFuture<Void> readiness;

    public GuildMembershipServiceImpl(
            @NonNull GuildServerStorage storage,
            @NonNull CompletableFuture<Void> readiness
    ) {
        this.storage = storage;
        this.readiness = readiness;
    }

    @Override
    public @NonNull CompletableFuture<Optional<GuildProfile>> findGuildByPlayer(
            @NonNull UUID playerId
    ) {
        Objects.requireNonNull(playerId, "Player identifier cannot be null");
        return readiness.thenCompose(ignored -> storage.findGuildByPlayer(playerId));
    }

    @Override
    public @NonNull CompletableFuture<Optional<GuildProfile>> findGuild(
            @NonNull UUID guildId
    ) {
        Objects.requireNonNull(guildId, "Guild identifier cannot be null");
        return readiness.thenCompose(ignored -> storage.findGuild(guildId));
    }
}
