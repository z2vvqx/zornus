package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.model.AxeFuserOperation;
import net.valoury.bloodstone.server.model.CombatResolution;
import net.valoury.bloodstone.server.model.EnchanterOperation;
import net.valoury.bloodstone.server.model.GuildLeaderboardEntry;
import net.valoury.bloodstone.server.model.LeaderboardMetric;
import net.valoury.bloodstone.server.model.PlayerData;
import net.valoury.bloodstone.server.model.PlayerLeaderboardEntry;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.model.RepairOperation;
import net.valoury.bloodstone.server.model.SoulboundRecovery;
import net.valoury.bloodstone.server.model.StorageSession;
import net.valoury.bloodstone.server.model.StorageType;
import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class BloodstonePostgresStorage implements BloodstoneStorage {

    private static final int CONNECTION_POOL_SIZE = 4;
    private static final int EXECUTOR_POOL_SIZE = 4;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;
    private final BloodstonePostgresPlayerRepository playerRepository;
    private final BloodstonePostgresLeaderboardRepository leaderboardRepository;
    private final BloodstonePostgresInventoryRepository inventoryRepository;
    private final BloodstonePostgresCombatRepository combatRepository;
    private final BloodstonePostgresSoulboundRepository soulboundRepository;
    private final BloodstonePostgresRandomBoxRepository randomBoxRepository;
    private final BloodstonePostgresEnchanterRepository enchanterRepository;
    private final BloodstonePostgresRepairRepository repairRepository;
    private final BloodstonePostgresAxeFuserRepository axeFuserRepository;
    private final CompletableFuture<Void> readiness;

    public BloodstonePostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl);
        configuration.setUsername(username);
        configuration.setPassword(password);
        configuration.setMaximumPoolSize(
                CONNECTION_POOL_SIZE
        );
        configuration.setDriverClassName("org.postgresql.Driver");
        configuration.setInitializationFailTimeout(-1);
        configuration.setConnectionTimeout(DatabaseDefaults.CONNECTION_ACQUISITION_TIMEOUT_MILLISECONDS);
        configuration.setValidationTimeout(DatabaseDefaults.CONNECTION_VALIDATION_TIMEOUT_MILLISECONDS);
        configuration.addDataSourceProperty(
                "connectTimeout", DatabaseDefaults.CONNECTION_ESTABLISHMENT_TIMEOUT_SECONDS);
        configuration.addDataSourceProperty(
                "socketTimeout", DatabaseDefaults.SOCKET_READ_TIMEOUT_SECONDS);
        configuration.addDataSourceProperty(
                "cancelSignalTimeout", DatabaseDefaults.CANCEL_SIGNAL_TIMEOUT_SECONDS);
        configuration.addDataSourceProperty("options", DatabaseDefaults.POSTGRESQL_SESSION_OPTIONS);

        this.dataSource = new HikariDataSource(configuration);
        this.databaseExecutor = new DatabaseExecutor(
                "bloodstone-database-",
                EXECUTOR_POOL_SIZE
        );
        this.playerRepository = new BloodstonePostgresPlayerRepository(
                dataSource,
                databaseExecutor
        );
        this.leaderboardRepository =
                new BloodstonePostgresLeaderboardRepository(
                        dataSource,
                        databaseExecutor
                );
        this.inventoryRepository =
                new BloodstonePostgresInventoryRepository(
                        dataSource,
                        databaseExecutor
                );
        this.combatRepository = new BloodstonePostgresCombatRepository(
                dataSource,
                databaseExecutor
        );
        BloodstonePostgresOperationStatements operationStatements =
                new BloodstonePostgresOperationStatements(
                        dataSource,
                        databaseExecutor
                );
        this.soulboundRepository = new BloodstonePostgresSoulboundRepository(
                dataSource,
                databaseExecutor,
                operationStatements
        );
        this.randomBoxRepository = new BloodstonePostgresRandomBoxRepository(
                dataSource,
                databaseExecutor,
                operationStatements
        );
        this.enchanterRepository = new BloodstonePostgresEnchanterRepository(
                dataSource,
                databaseExecutor,
                operationStatements
        );
        this.repairRepository = new BloodstonePostgresRepairRepository(
                dataSource,
                databaseExecutor,
                operationStatements
        );
        this.axeFuserRepository = new BloodstonePostgresAxeFuserRepository(
                dataSource,
                databaseExecutor,
                operationStatements
        );
        this.readiness = databaseExecutor.run(
                () -> BloodstonePostgresSchema.initialize(dataSource)
        );
        this.readiness.whenCompleteAsync((ignored, exception) -> {
            if (exception != null) {
                close();
            }
        });
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return readiness;
    }

    @Override
    public CompletableFuture<PlayerData> loadOrCreatePlayer(
            @NonNull UUID playerId,
            @NonNull String username
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        return playerRepository.loadOrCreate(playerId, username);
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> fetchPlayer(
            @NonNull UUID playerId
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        return playerRepository.fetch(playerId);
    }

    @Override
    public CompletableFuture<CombatResolutionOutcome> resolveCombat(
            @NonNull CombatResolution resolution
    ) {
        Objects.requireNonNull(resolution, "Combat resolution cannot be null");
        return combatRepository.resolve(resolution);
    }

    @Override
    public CompletableFuture<Boolean> recordDeath(
            @NonNull UUID eventId,
            @NonNull UUID victimId,
            UUID victimGuildId,
            @NonNull Instant occurredAt
    ) {
        Objects.requireNonNull(eventId, "Event ID cannot be null");
        Objects.requireNonNull(victimId, "Victim ID cannot be null");
        Objects.requireNonNull(occurredAt, "Occurrence time cannot be null");
        return combatRepository.recordDeath(
                eventId,
                victimId,
                victimGuildId,
                occurredAt
        );
    }

    @Override
    public CompletableFuture<ExtraStorageUnlockOutcome> unlockExtraStorage(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            @NonNull Instant now
    ) {
        return inventoryRepository.unlockExtraStorage(
                operationId,
                playerId,
                now
        );
    }

    @Override
    public CompletableFuture<SoulboundRecovery> reserveSoulboundRecovery(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] itemPayload,
            @NonNull Instant now
    ) {
        return soulboundRepository.reserveSoulboundRecovery(
                operationId,
                playerId,
                itemPayload,
                now
        );
    }

    @Override
    public CompletableFuture<List<SoulboundRecovery>> fetchSoulboundRecoveries(
            @NonNull UUID playerId
    ) {
        return soulboundRepository.fetchSoulboundRecoveries(playerId);
    }

    @Override
    public CompletableFuture<Boolean> completeSoulboundRecovery(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return soulboundRepository.completeSoulboundRecovery(
                operationId,
                playerId
        );
    }

    @Override
    public CompletableFuture<RandomBoxReserveOutcome> reserveRandomBox(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            @NonNull String rewardId,
            byte @NonNull [] rewardPayload,
            int maximumFreeUses,
            int paidBloodCost,
            boolean paidUseAllowed,
            @NonNull Instant now
    ) {
        return randomBoxRepository.reserveRandomBox(
                operationId,
                playerId,
                rewardId,
                rewardPayload,
                maximumFreeUses,
                paidBloodCost,
                paidUseAllowed,
                now
        );
    }

    @Override
    public CompletableFuture<List<RandomBoxOperation>> fetchRandomBoxRecoveries(
            @NonNull UUID playerId
    ) {
        return randomBoxRepository.fetchRandomBoxRecoveries(playerId);
    }

    @Override
    public CompletableFuture<Boolean> completeRandomBox(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return randomBoxRepository.completeRandomBox(operationId, playerId);
    }

    @Override
    public CompletableFuture<EnchanterReserveOutcome> reserveEnchanterOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            @NonNull String offerKey,
            @NonNull Instant now,
            @NonNull Duration cooldown,
            byte @NonNull [] originalItemPayload
    ) {
        return enchanterRepository.reserveEnchanterOperation(
                operationId,
                playerId,
                offerKey,
                now,
                cooldown,
                originalItemPayload
        );
    }

    @Override
    public CompletableFuture<Boolean> markEnchanterOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] enchantedItemPayload
    ) {
        return enchanterRepository.markEnchanterOperationReady(
                operationId,
                playerId,
                enchantedItemPayload
        );
    }

    @Override
    public CompletableFuture<List<EnchanterOperation>> fetchEnchanterRecoveries(
            @NonNull UUID playerId
    ) {
        return enchanterRepository.fetchEnchanterRecoveries(playerId);
    }

    @Override
    public CompletableFuture<Boolean> completeEnchanterOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return enchanterRepository.completeEnchanterOperation(
                operationId,
                playerId
        );
    }

    @Override
    public CompletableFuture<RepairReserveOutcome> reserveRepairOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] originalItemPayload,
            @NonNull Instant now
    ) {
        return repairRepository.reserveRepairOperation(
                operationId,
                playerId,
                originalItemPayload,
                now
        );
    }

    @Override
    public CompletableFuture<Boolean> markRepairOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] repairedItemPayload
    ) {
        return repairRepository.markRepairOperationReady(
                operationId,
                playerId,
                repairedItemPayload
        );
    }

    @Override
    public CompletableFuture<List<RepairOperation>> fetchRepairRecoveries(
            @NonNull UUID playerId
    ) {
        return repairRepository.fetchRepairRecoveries(playerId);
    }

    @Override
    public CompletableFuture<Boolean> completeRepairOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return repairRepository.completeRepairOperation(
                operationId,
                playerId
        );
    }

    @Override
    public CompletableFuture<AxeFuserReserveOutcome> reserveAxeFuserOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] originalAxesPayload,
            int bloodAlloyCost,
            @NonNull Instant now
    ) {
        return axeFuserRepository.reserveAxeFuserOperation(
                operationId,
                playerId,
                originalAxesPayload,
                bloodAlloyCost,
                now
        );
    }

    @Override
    public CompletableFuture<Boolean> markAxeFuserOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] fusedAxePayload
    ) {
        return axeFuserRepository.markAxeFuserOperationReady(
                operationId,
                playerId,
                fusedAxePayload
        );
    }

    @Override
    public CompletableFuture<List<AxeFuserOperation>> fetchAxeFuserRecoveries(
            @NonNull UUID playerId
    ) {
        return axeFuserRepository.fetchAxeFuserRecoveries(playerId);
    }

    @Override
    public CompletableFuture<Boolean> completeAxeFuserOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return axeFuserRepository.completeAxeFuserOperation(
                operationId,
                playerId
        );
    }

    @Override
    public CompletableFuture<StorageOpenOutcome> openStorage(
            @NonNull UUID playerId,
            @NonNull StorageType storageType,
            @NonNull UUID sessionToken,
            @NonNull Instant now,
            @NonNull Duration leaseDuration
    ) {
        return inventoryRepository.open(
                playerId,
                storageType,
                sessionToken,
                now,
                leaseDuration
        );
    }

    @Override
    public CompletableFuture<StorageWriteOutcome> checkpointStorage(
            @NonNull StorageSession session,
            byte[] contentsPayload,
            @NonNull Instant now,
            @NonNull Duration leaseDuration
    ) {
        return inventoryRepository.checkpoint(
                session,
                contentsPayload,
                now,
                leaseDuration
        );
    }

    @Override
    public CompletableFuture<StorageWriteOutcome> closeStorage(
            @NonNull StorageSession session,
            byte[] contentsPayload,
            @NonNull Instant now
    ) {
        return inventoryRepository.close(session, contentsPayload, now);
    }

    @Override
    public CompletableFuture<List<PlayerLeaderboardEntry>> fetchPlayerLeaderboard(
            @NonNull LeaderboardMetric metric
    ) {
        Objects.requireNonNull(metric, "Leaderboard metric cannot be null");
        return leaderboardRepository.fetchPlayers(metric);
    }

    @Override
    public CompletableFuture<List<GuildLeaderboardEntry>> fetchGuildLeaderboard(
            @NonNull LeaderboardMetric metric
    ) {
        Objects.requireNonNull(metric, "Leaderboard metric cannot be null");
        return leaderboardRepository.fetchGuilds(metric);
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(
                    SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            )) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        RuntimeException releaseFailure = null;
        try {
            inventoryRepository.releaseOwnedSessions();
        } catch (RuntimeException exception) {
            releaseFailure = exception;
        } finally {
            dataSource.close();
        }
        if (releaseFailure != null) {
            throw releaseFailure;
        }
    }

}
