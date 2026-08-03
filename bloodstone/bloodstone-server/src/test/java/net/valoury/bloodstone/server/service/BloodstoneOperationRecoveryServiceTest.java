package net.valoury.bloodstone.server.service;

import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.model.SoulboundRecovery;
import net.valoury.bloodstone.server.storage.BloodstoneStorage;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class BloodstoneOperationRecoveryServiceTest {

    private static final UUID PLAYER_ID =
            UUID.fromString("e7161f11-1536-4fcc-9639-937461677ee0");
    private static final UUID OPERATION_ID =
            UUID.fromString("7159f375-a8c0-4f73-8192-f3c13a94c465");

    @Test
    void lateRecoveryCannotDeliverIntoANewerPlayerSession() {
        CompletableFuture<List<RandomBoxOperation>> pendingRecoveries =
                new CompletableFuture<>();
        AtomicInteger completedOperations = new AtomicInteger();
        BloodstoneStorage storage = storage(
                pendingRecoveries,
                completedOperations
        );
        BloodstonePlayerSessionRegistry playerSessions =
                new BloodstonePlayerSessionRegistry();
        UUID originalGeneration = loadedSession(playerSessions);
        BloodstoneOperationRecoveryService recoveryService = recoveryService(
                storage,
                playerSessions
        );

        CompletableFuture<Void> recovery =
                recoveryService.recoverRandomBoxOperation(
                        onlinePlayer(),
                        OPERATION_ID
                );
        playerSessions.endSession(PLAYER_ID);
        UUID replacementGeneration = loadedSession(playerSessions);
        pendingRecoveries.complete(List.of(randomBoxOperation()));
        recovery.join();

        assertEquals(0, completedOperations.get());
        assertEquals(
                replacementGeneration,
                playerSessions.currentGeneration(PLAYER_ID).orElseThrow()
        );
        assertNotEquals(originalGeneration, replacementGeneration);
    }

    @Test
    void endedJoinSessionStopsRemainingRecoveryQueries() {
        CompletableFuture<List<SoulboundRecovery>> pendingRecoveries =
                new CompletableFuture<>();
        AtomicInteger storageCalls = new AtomicInteger();
        BloodstoneStorage storage = (BloodstoneStorage) Proxy.newProxyInstance(
                BloodstoneStorage.class.getClassLoader(),
                new Class<?>[]{BloodstoneStorage.class},
                (proxy, method, arguments) -> {
                    storageCalls.incrementAndGet();
                    if (method.getName().equals("fetchSoulboundRecoveries")) {
                        return pendingRecoveries;
                    }
                    return CompletableFuture.completedFuture(List.of());
                }
        );
        BloodstonePlayerSessionRegistry playerSessions =
                new BloodstonePlayerSessionRegistry();
        UUID sessionGeneration = loadedSession(playerSessions);
        BloodstoneOperationRecoveryService recoveryService = recoveryService(
                storage,
                playerSessions
        );

        CompletableFuture<Void> recovery = recoveryService.recoverOnJoin(
                onlinePlayer(),
                sessionGeneration
        );
        playerSessions.endSession(PLAYER_ID);
        pendingRecoveries.complete(List.of());
        recovery.join();

        assertEquals(1, storageCalls.get());
    }

    private static UUID loadedSession(
            BloodstonePlayerSessionRegistry playerSessions
    ) {
        UUID generation = playerSessions.beginLoading(PLAYER_ID);
        playerSessions.storeLoadedProfile(
                PLAYER_ID,
                generation,
                new PlayerProfile(
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
                        0
                )
        );
        playerSessions.finishLoading(PLAYER_ID, generation, true);
        return generation;
    }

    private static BloodstoneOperationRecoveryService recoveryService(
            BloodstoneStorage storage,
            BloodstonePlayerSessionRegistry playerSessions
    ) {
        BloodstoneItemIdentityService itemIdentity =
                new BloodstoneItemIdentityService();
        BloodstoneCurrencyService currencyService =
                new BloodstoneCurrencyService(itemIdentity);
        BloodstoneReservedItemDeliveryService deliveryService =
                new BloodstoneReservedItemDeliveryService(
                        itemIdentity,
                        playerSessions,
                        Runnable::run,
                        new BloodstonePresentationService(),
                        new BloodstoneMessageService()
                );
        return new BloodstoneOperationRecoveryService(
                storage,
                currencyService,
                playerSessions,
                deliveryService,
                Runnable::run
        );
    }

    private static BloodstoneStorage storage(
            CompletableFuture<List<RandomBoxOperation>> pendingRecoveries,
            AtomicInteger completedOperations
    ) {
        return (BloodstoneStorage) Proxy.newProxyInstance(
                BloodstoneStorage.class.getClassLoader(),
                new Class<?>[]{BloodstoneStorage.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "fetchRandomBoxRecoveries" -> pendingRecoveries;
                    case "completeRandomBox" -> {
                        completedOperations.incrementAndGet();
                        yield CompletableFuture.completedFuture(true);
                    }
                    default -> throw new UnsupportedOperationException(
                            method.getName()
                    );
                }
        );
    }

    private static Player onlinePlayer() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> PLAYER_ID;
                    case "isOnline" -> true;
                    default -> throw new UnsupportedOperationException(
                            method.getName()
                    );
                }
        );
    }

    private static RandomBoxOperation randomBoxOperation() {
        return new RandomBoxOperation(
                OPERATION_ID,
                PLAYER_ID,
                "test_reward",
                new byte[]{1},
                true,
                0,
                Instant.EPOCH
        );
    }
}
