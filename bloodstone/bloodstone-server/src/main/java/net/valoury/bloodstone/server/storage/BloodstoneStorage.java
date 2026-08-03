package net.valoury.bloodstone.server.storage;

import java.util.concurrent.CompletableFuture;

public interface BloodstoneStorage extends
        BloodstonePlayerStorage,
        BloodstoneCombatStorage,
        BloodstoneOperationStorage,
        BloodstoneInventoryStorage,
        BloodstoneLeaderboardStorage,
        AutoCloseable {

    CompletableFuture<Void> initialize();

    @Override
    void close();
}
