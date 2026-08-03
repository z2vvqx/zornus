package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.CombatResolution;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BloodstoneCombatStorage {

    CompletableFuture<CombatResolutionOutcome> resolveCombat(
            @NonNull CombatResolution resolution
    );

    CompletableFuture<Boolean> recordDeath(
            @NonNull UUID eventId,
            @NonNull UUID victimId,
            @Nullable UUID victimGuildId,
            @NonNull Instant occurredAt
    );
}
