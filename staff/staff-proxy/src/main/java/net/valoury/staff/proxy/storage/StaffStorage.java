package net.valoury.staff.proxy.storage;

import net.valoury.shared.model.PlayerRecord;
import net.valoury.staff.proxy.model.AddressFingerprint;
import net.valoury.staff.proxy.model.ConnectionEdge;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StaffStorage {
    @NonNull CompletableFuture<Void> recordConnection(
            @NonNull UUID playerUuid,
            @NonNull String username,
            @NonNull AddressFingerprint addressFingerprint
    );

    @NonNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(
            @NonNull String username
    );

    @NonNull CompletableFuture<List<ConnectionEdge>> fetchConnectedComponent(
            @NonNull UUID playerUuid
    );

    @NonNull CompletableFuture<Void> cleanupExpiredConnections();

    void close();
}
