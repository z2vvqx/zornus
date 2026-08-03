package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.model.PlayerData;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BloodstoneOnlineProfileRefresherTest {

    private static final UUID PLAYER_ID =
            UUID.fromString("e7161f11-1536-4fcc-9639-937461677ee0");

    @Test
    void lateDatabaseCompletionCannotRecreateAQuitPlayerSession() {
        CompletableFuture<Optional<PlayerData>> pendingFetch =
                new CompletableFuture<>();
        AtomicInteger fetches = new AtomicInteger();
        AtomicInteger identityUpserts = new AtomicInteger();
        BloodstoneStorage storage = (BloodstoneStorage) Proxy.newProxyInstance(
                BloodstoneStorage.class.getClassLoader(),
                new Class<?>[]{BloodstoneStorage.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "fetchPlayer" -> {
                        fetches.incrementAndGet();
                        yield pendingFetch;
                    }
                    case "loadOrCreatePlayer" -> {
                        identityUpserts.incrementAndGet();
                        throw new AssertionError(
                                "Profile refresh must not upsert player identity"
                        );
                    }
                    default -> throw new UnsupportedOperationException(
                            method.getName()
                    );
                }
        );
        BloodstonePlayerSessionRegistry sessions =
                new BloodstonePlayerSessionRegistry();
        UUID generation = sessions.beginLoading(PLAYER_ID);
        sessions.storeLoadedProfile(
                PLAYER_ID,
                generation,
                profile(1)
        );
        sessions.finishLoading(PLAYER_ID, generation, true);
        BloodstoneOnlineProfileRefresher refresher =
                new BloodstoneOnlineProfileRefresher(
                        storage,
                        sessions,
                        Runnable::run,
                        Logger.getAnonymousLogger()
                );

        refresher.refresh(Set.of(PLAYER_ID));
        sessions.endSession(PLAYER_ID);
        pendingFetch.complete(Optional.of(new PlayerData(profile(2))));

        assertEquals(1, fetches.get());
        assertEquals(0, identityUpserts.get());
        assertTrue(sessions.profile(PLAYER_ID).isEmpty());
    }

    @Test
    void offlinePlayerDoesNotTriggerAnyDatabaseOperation() {
        AtomicInteger calls = new AtomicInteger();
        BloodstoneStorage storage = (BloodstoneStorage) Proxy.newProxyInstance(
                BloodstoneStorage.class.getClassLoader(),
                new Class<?>[]{BloodstoneStorage.class},
                (proxy, method, arguments) -> {
                    calls.incrementAndGet();
                    throw new AssertionError(
                            "Offline refresh must not access storage"
                    );
                }
        );
        BloodstoneOnlineProfileRefresher refresher =
                new BloodstoneOnlineProfileRefresher(
                        storage,
                        new BloodstonePlayerSessionRegistry(),
                        Runnable::run,
                        Logger.getAnonymousLogger()
                );

        refresher.refresh(Set.of(PLAYER_ID));

        assertEquals(0, calls.get());
    }

    private static PlayerProfile profile(long version) {
        return new PlayerProfile(
                PLAYER_ID,
                "MMAJED",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                version
        );
    }
}
