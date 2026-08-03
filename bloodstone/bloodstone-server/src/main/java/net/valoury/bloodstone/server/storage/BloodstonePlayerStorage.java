package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.PlayerData;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BloodstonePlayerStorage {

    CompletableFuture<PlayerData> loadOrCreatePlayer(
            @NonNull UUID playerId,
            @NonNull String username
    );

    CompletableFuture<Optional<PlayerData>> fetchPlayer(
            @NonNull UUID playerId
    );
}
