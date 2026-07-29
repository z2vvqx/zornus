package net.valoury.guilds.server;

import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.server.service.GuildMembershipServiceImpl;
import net.valoury.guilds.server.storage.GuildServerPostgresStorage;
import net.valoury.guilds.server.storage.GuildServerStorage;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public final class GuildsServerModule implements AutoCloseable {

    private final GuildServerStorage storage;
    private final CompletableFuture<Void> readiness;
    private final GuildMembershipService membershipService;

    public GuildsServerModule() {
        this.storage = new GuildServerPostgresStorage(
                GuildsServerConstants.POSTGRESQL_URL,
                GuildsServerConstants.POSTGRESQL_USER,
                GuildsServerConstants.POSTGRESQL_PASSWORD
        );
        this.readiness = storage.validateSchema();
        this.membershipService = new GuildMembershipServiceImpl(storage, readiness);
    }

    public @NonNull CompletableFuture<Void> initialize() {
        return readiness;
    }

    public @NonNull GuildMembershipService memberships() {
        return membershipService;
    }

    @Override
    public void close() {
        storage.close();
    }
}
