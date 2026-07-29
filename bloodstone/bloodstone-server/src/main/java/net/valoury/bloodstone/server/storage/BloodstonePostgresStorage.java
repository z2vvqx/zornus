package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.model.CombatResolution;
import net.valoury.bloodstone.server.model.EnchanterOperation;
import net.valoury.bloodstone.server.model.GuildLeaderboardEntry;
import net.valoury.bloodstone.server.model.LeaderboardMetric;
import net.valoury.bloodstone.server.model.LeaderboardSnapshot;
import net.valoury.bloodstone.server.model.PlayerData;
import net.valoury.bloodstone.server.model.PlayerLeaderboardEntry;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.model.RandomBoxWindow;
import net.valoury.bloodstone.server.model.RampageTransition;
import net.valoury.bloodstone.server.model.RecoverableOperationState;
import net.valoury.bloodstone.server.model.RepairOperation;
import net.valoury.bloodstone.server.model.SoulboundRecovery;
import net.valoury.bloodstone.server.model.StorageSession;
import net.valoury.bloodstone.server.model.StorageType;
import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class BloodstonePostgresStorage implements BloodstoneStorage {

    private static final int CONNECTION_POOL_SIZE = 4;
    private static final int EXECUTOR_POOL_SIZE = 4;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final int TRANSACTION_MAXIMUM_ATTEMPTS = 3;

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;
    private final CompletableFuture<Void> readiness;
    private final Set<UUID> ownedStorageSessions = ConcurrentHashMap.newKeySet();

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
        this.readiness = databaseExecutor.run(this::initializeSchema);
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

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement rootTableLookup = connection.prepareStatement(
                    "SELECT to_regclass('public.bloodstone_players') IS NOT NULL"
            ); ResultSet resultSet = rootTableLookup.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("PostgreSQL did not return a schema-existence result");
                }
                if (resultSet.getBoolean(1)) {
                    connection.rollback();
                    return;
                }
            }

            try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_players (
                        player_id UUID PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        kills INTEGER NOT NULL DEFAULT 0 CHECK (kills >= 0),
                        deaths INTEGER NOT NULL DEFAULT 0 CHECK (deaths >= 0),
                        assists INTEGER NOT NULL DEFAULT 0 CHECK (assists >= 0),
                        carries INTEGER NOT NULL DEFAULT 0 CHECK (carries >= 0),
                        dominations INTEGER NOT NULL DEFAULT 0 CHECK (dominations >= 0),
                        revenges INTEGER NOT NULL DEFAULT 0 CHECK (revenges >= 0),
                        current_rampage INTEGER NOT NULL DEFAULT 0 CHECK (current_rampage >= 0),
                        best_rampage INTEGER NOT NULL DEFAULT 0 CHECK (best_rampage >= current_rampage),
                        extra_storage_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
                        version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
                        last_joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_players_username_lower
                    ON bloodstone_players (LOWER(username))
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_players_kills
                    ON bloodstone_players (kills DESC, player_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_players_current_rampage
                    ON bloodstone_players (current_rampage DESC, player_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_players_best_rampage
                    ON bloodstone_players (best_rampage DESC, player_id)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_extra_storage_purchases (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        purchased_at TIMESTAMPTZ NOT NULL
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_guild_statistics (
                        guild_id UUID PRIMARY KEY,
                        kills INTEGER NOT NULL DEFAULT 0 CHECK (kills >= 0),
                        deaths INTEGER NOT NULL DEFAULT 0 CHECK (deaths >= 0),
                        current_rampage INTEGER NOT NULL DEFAULT 0 CHECK (current_rampage >= 0),
                        best_rampage INTEGER NOT NULL DEFAULT 0 CHECK (best_rampage >= current_rampage),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_guild_statistics_kills
                    ON bloodstone_guild_statistics (kills DESC, guild_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_guild_statistics_current_rampage
                    ON bloodstone_guild_statistics (current_rampage DESC, guild_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_guild_statistics_best_rampage
                    ON bloodstone_guild_statistics (best_rampage DESC, guild_id)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_combat_events (
                        event_id UUID PRIMARY KEY,
                        killer_id UUID NOT NULL,
                        victim_id UUID NOT NULL,
                        killer_current_rampage INTEGER NOT NULL,
                        killer_best_rampage INTEGER NOT NULL,
                        new_player_best BOOLEAN NOT NULL,
                        killer_guild_current_rampage INTEGER,
                        killer_guild_best_rampage INTEGER,
                        new_guild_best BOOLEAN NOT NULL,
                        occurred_at TIMESTAMPTZ NOT NULL,
                        processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        CHECK (
                            (killer_guild_current_rampage IS NULL
                                AND killer_guild_best_rampage IS NULL
                                AND NOT new_guild_best)
                            OR
                            (killer_guild_current_rampage IS NOT NULL
                                AND killer_guild_best_rampage IS NOT NULL)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_uncredited_death_events (
                        event_id UUID PRIMARY KEY,
                        victim_id UUID NOT NULL,
                        victim_guild_id UUID,
                        occurred_at TIMESTAMPTZ NOT NULL,
                        processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_soulbound_recoveries (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        item_payload BYTEA NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL,
                        completed_at TIMESTAMPTZ
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_soulbound_player
                    ON bloodstone_soulbound_recoveries (player_id, created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_random_box_usage (
                        player_id UUID PRIMARY KEY REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        window_start TIMESTAMPTZ,
                        free_used INTEGER NOT NULL DEFAULT 0 CHECK (free_used >= 0)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_random_box_operations (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        reward_id VARCHAR(128) NOT NULL,
                        reward_payload BYTEA NOT NULL,
                        free_use BOOLEAN NOT NULL,
                        blood_cost INTEGER NOT NULL CHECK (blood_cost >= 0),
                        created_at TIMESTAMPTZ NOT NULL,
                        completed_at TIMESTAMPTZ
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_random_box_operations_player
                    ON bloodstone_random_box_operations (player_id, created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_enchanter_offer_cooldowns (
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        offer_key VARCHAR(128) NOT NULL,
                        available_at TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (player_id, offer_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_enchanter_operations (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        original_item_payload BYTEA NOT NULL,
                        enchanted_item_payload BYTEA,
                        state VARCHAR(16) NOT NULL CHECK (state IN ('RESERVED', 'READY')),
                        created_at TIMESTAMPTZ NOT NULL,
                        completed_at TIMESTAMPTZ,
                        CHECK (
                            (state = 'RESERVED' AND enchanted_item_payload IS NULL)
                            OR (state = 'READY' AND enchanted_item_payload IS NOT NULL)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_enchanter_operations_player
                    ON bloodstone_enchanter_operations (player_id, created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_repair_operations (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        original_item_payload BYTEA NOT NULL,
                        repaired_item_payload BYTEA,
                        state VARCHAR(16) NOT NULL CHECK (state IN ('RESERVED', 'READY')),
                        created_at TIMESTAMPTZ NOT NULL,
                        completed_at TIMESTAMPTZ,
                        CHECK (
                            (state = 'RESERVED' AND repaired_item_payload IS NULL)
                            OR (state = 'READY' AND repaired_item_payload IS NOT NULL)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_repair_operations_player
                    ON bloodstone_repair_operations (player_id, created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_storage_contents (
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        storage_type VARCHAR(16) NOT NULL CHECK (
                            storage_type IN ('DEFAULT', 'IRON', 'GOLD', 'DIAMOND', 'EMERALD', 'EXTRA')
                        ),
                        contents_payload BYTEA,
                        version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
                        session_token UUID,
                        lease_expires_at TIMESTAMPTZ,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (player_id, storage_type),
                        CHECK (
                            (session_token IS NULL AND lease_expires_at IS NULL)
                            OR (session_token IS NOT NULL AND lease_expires_at IS NOT NULL)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_storage_leases
                    ON bloodstone_storage_contents (lease_expires_at)
                    WHERE session_token IS NOT NULL
                    """);
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize Bloodstone database schema", exception);
        }
    }

    @Override
    public CompletableFuture<PlayerData> loadPlayer(@NonNull UUID playerId, @NonNull String username) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        validateUsername(username);
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO bloodstone_players (player_id, username, last_joined_at)
                            VALUES (?, ?, NOW())
                            ON CONFLICT (player_id) DO UPDATE
                            SET username = EXCLUDED.username, last_joined_at = EXCLUDED.last_joined_at
                            """)) {
                        statement.setObject(1, playerId);
                        statement.setString(2, username);
                        statement.executeUpdate();
                    }
                    PlayerData result;
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT p.player_id, p.username, p.kills, p.deaths, p.assists, p.carries,
                                   p.dominations, p.revenges, p.current_rampage, p.best_rampage,
                                   p.extra_storage_unlocked, p.version
                            FROM bloodstone_players p
                            WHERE p.player_id = ?
                            """)) {
                        statement.setObject(1, playerId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                throw new SQLException("Player disappeared while loading: " + playerId);
                            }
                            result = mapPlayerData(resultSet);
                        }
                    }
                    connection.commit();
                    return result;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to load Bloodstone player " + playerId, exception);
            }
        });
    }

    @Override
    public CompletableFuture<ProfileSaveOutcome> savePlayerProfile(@NonNull PlayerProfile profile) {
        Objects.requireNonNull(profile, "Profile cannot be null");
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE bloodstone_players
                    SET username = ?, kills = ?, deaths = ?, assists = ?, carries = ?,
                        dominations = ?, revenges = ?, current_rampage = ?, best_rampage = ?,
                        extra_storage_unlocked = ?, version = version + 1
                    WHERE player_id = ? AND version = ?
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, profile.username());
                statement.setInt(2, profile.kills());
                statement.setInt(3, profile.deaths());
                statement.setInt(4, profile.assists());
                statement.setInt(5, profile.carries());
                statement.setInt(6, profile.dominations());
                statement.setInt(7, profile.revenges());
                statement.setInt(8, profile.currentRampage());
                statement.setInt(9, profile.bestRampage());
                statement.setBoolean(10, profile.extraStorageUnlocked());
                statement.setObject(11, profile.playerId());
                statement.setLong(12, profile.version());
                if (statement.executeUpdate() == 1) {
                    return ProfileSaveOutcome.SAVED;
                }
                return playerExists(connection, profile.playerId())
                        ? ProfileSaveOutcome.VERSION_CONFLICT
                        : ProfileSaveOutcome.PLAYER_NOT_FOUND;
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to save Bloodstone profile " + profile.playerId(), exception);
            }
        });
    }

    @Override
    public CompletableFuture<CombatResolutionOutcome> resolveCombat(
            @NonNull CombatResolution resolution
    ) {
        Objects.requireNonNull(resolution, "Combat resolution cannot be null");
        return databaseExecutor.supply(() -> executeCombatWithRetry(resolution));
    }

    private CombatResolutionOutcome executeCombatWithRetry(CombatResolution resolution) {
        SQLException lastFailure = null;
        for (int attempt = 1;
                attempt <= TRANSACTION_MAXIMUM_ATTEMPTS;
                attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);
                try {
                    CombatResolutionOutcome result = executeCombat(connection, resolution);
                    connection.commit();
                    return result;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    if (!isRetryableTransactionFailure(exception)
                            || attempt
                            == TRANSACTION_MAXIMUM_ATTEMPTS) {
                        throw exception;
                    }
                    lastFailure = exception;
                }
            } catch (SQLException exception) {
                if (!isRetryableTransactionFailure(exception)
                        || attempt
                        == TRANSACTION_MAXIMUM_ATTEMPTS) {
                    throw new RuntimeException(
                            "Failed to resolve Bloodstone combat event " + resolution.eventId(),
                            exception
                    );
                }
                lastFailure = exception;
            }
        }
        throw new RuntimeException(
                "Failed to resolve Bloodstone combat event " + resolution.eventId(),
                lastFailure
        );
    }

    private CombatResolutionOutcome executeCombat(
            Connection connection,
            CombatResolution resolution
    ) throws SQLException {
        lockOperation(connection, resolution.eventId());
        CombatResolutionOutcome existing = findCombatOutcome(connection, resolution.eventId());
        if (existing != null) {
            return existing;
        }

        Set<UUID> creditedPlayers = new HashSet<>(resolution.assistPlayerIds());
        creditedPlayers.add(resolution.killerId());
        creditedPlayers.add(resolution.victimId());
        if (resolution.carryPlayerId() != null) {
            creditedPlayers.add(resolution.carryPlayerId());
        }
        lockPlayers(connection, creditedPlayers);

        RampageBefore playerBefore = fetchPlayerRampage(connection, resolution.killerId());
        Map<UUID, PlayerDelta> playerDeltas = new HashMap<>();
        playerDeltas.computeIfAbsent(resolution.killerId(), ignored -> new PlayerDelta())
                .creditKill(resolution.domination(), resolution.revenge());
        playerDeltas.computeIfAbsent(resolution.victimId(), ignored -> new PlayerDelta())
                .creditDeath();
        for (UUID assistPlayerId : resolution.assistPlayerIds()) {
            playerDeltas.computeIfAbsent(assistPlayerId, ignored -> new PlayerDelta())
                    .creditAssist();
        }
        if (resolution.carryPlayerId() != null) {
            playerDeltas.computeIfAbsent(resolution.carryPlayerId(), ignored -> new PlayerDelta())
                    .creditCarry();
        }
        updatePlayerStatistics(connection, playerDeltas);

        RampageTransition playerTransition = RampageTransition.afterKill(
                playerBefore.current(),
                playerBefore.best()
        );
        int killerCurrentRampage = playerTransition.current();
        int killerBestRampage = playerTransition.best();
        boolean newPlayerBest = playerTransition.newBest();

        Integer killerGuildCurrentRampage = null;
        Integer killerGuildBestRampage = null;
        boolean newGuildBest = false;
        if (resolution.killerGuildId() != null) {
            Map<UUID, GuildDelta> guildDeltas = new HashMap<>();
            guildDeltas.computeIfAbsent(resolution.killerGuildId(), ignored -> new GuildDelta())
                    .creditKill();
            if (resolution.victimGuildId() != null) {
                guildDeltas.computeIfAbsent(resolution.victimGuildId(), ignored -> new GuildDelta())
                        .creditDeath();
            }
            RampageBefore guildBefore = fetchGuildRampage(
                    connection, resolution.killerGuildId());
            updateGuildStatistics(connection, guildDeltas);
            boolean sameGuildReset = resolution.killerGuildId().equals(resolution.victimGuildId());
            RampageTransition guildTransition = sameGuildReset
                    ? RampageTransition.afterDeathThenKill(guildBefore.best())
                    : RampageTransition.afterKill(guildBefore.current(), guildBefore.best());
            killerGuildCurrentRampage = guildTransition.current();
            killerGuildBestRampage = guildTransition.best();
            newGuildBest = guildTransition.newBest();
        } else if (resolution.victimGuildId() != null) {
            Map<UUID, GuildDelta> guildDeltas = new HashMap<>();
            guildDeltas.computeIfAbsent(resolution.victimGuildId(), ignored -> new GuildDelta())
                    .creditDeath();
            updateGuildStatistics(connection, guildDeltas);
        }

        CombatResolutionOutcome outcome = new CombatResolutionOutcome(
                true,
                killerCurrentRampage,
                killerBestRampage,
                newPlayerBest,
                killerGuildCurrentRampage,
                killerGuildBestRampage,
                newGuildBest
        );
        insertCombatOutcome(connection, resolution, outcome);
        return outcome;
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
        return databaseExecutor.supply(() -> executeDeathWithRetry(
                eventId, victimId, victimGuildId, occurredAt));
    }

    private boolean executeDeathWithRetry(
            UUID eventId,
            UUID victimId,
            UUID victimGuildId,
            Instant occurredAt
    ) {
        SQLException lastFailure = null;
        for (int attempt = 1;
                attempt <= TRANSACTION_MAXIMUM_ATTEMPTS;
                attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);
                try {
                    lockOperation(connection, eventId);
                    if (uncreditedDeathExists(connection, eventId)) {
                        connection.commit();
                        return false;
                    }
                    lockPlayers(connection, Set.of(victimId));
                    Map<UUID, PlayerDelta> playerDeltas = new HashMap<>();
                    playerDeltas.computeIfAbsent(victimId, ignored -> new PlayerDelta())
                            .creditDeath();
                    updatePlayerStatistics(connection, playerDeltas);
                    if (victimGuildId != null) {
                        Map<UUID, GuildDelta> guildDeltas = new HashMap<>();
                        guildDeltas.computeIfAbsent(victimGuildId, ignored -> new GuildDelta())
                                .creditDeath();
                        updateGuildStatistics(connection, guildDeltas);
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO bloodstone_uncredited_death_events
                                (event_id, victim_id, victim_guild_id, occurred_at)
                            VALUES (?, ?, ?, ?)
                            """)) {
                        statement.setObject(1, eventId);
                        statement.setObject(2, victimId);
                        statement.setObject(3, victimGuildId);
                        setInstant(statement, 4, occurredAt);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return true;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    if (!isRetryableTransactionFailure(exception)
                            || attempt
                            == TRANSACTION_MAXIMUM_ATTEMPTS) {
                        throw exception;
                    }
                    lastFailure = exception;
                }
            } catch (SQLException exception) {
                if (!isRetryableTransactionFailure(exception)
                        || attempt
                        == TRANSACTION_MAXIMUM_ATTEMPTS) {
                    throw new RuntimeException(
                            "Failed to record uncredited death " + eventId, exception);
                }
                lastFailure = exception;
            }
        }
        throw new RuntimeException("Failed to record uncredited death " + eventId, lastFailure);
    }

    private boolean uncreditedDeathExists(Connection connection, UUID eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM bloodstone_uncredited_death_events WHERE event_id = ?
                """)) {
            statement.setObject(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private CombatResolutionOutcome findCombatOutcome(
            Connection connection,
            UUID eventId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT killer_current_rampage, killer_best_rampage, new_player_best,
                       killer_guild_current_rampage, killer_guild_best_rampage, new_guild_best
                FROM bloodstone_combat_events
                WHERE event_id = ?
                """)) {
            statement.setObject(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                Integer guildCurrent = getNullableInteger(
                        resultSet, "killer_guild_current_rampage");
                Integer guildBest = getNullableInteger(
                        resultSet, "killer_guild_best_rampage");
                return new CombatResolutionOutcome(
                        false,
                        resultSet.getInt("killer_current_rampage"),
                        resultSet.getInt("killer_best_rampage"),
                        resultSet.getBoolean("new_player_best"),
                        guildCurrent,
                        guildBest,
                        resultSet.getBoolean("new_guild_best")
                );
            }
        }
    }

    private void insertCombatOutcome(
            Connection connection,
            CombatResolution resolution,
            CombatResolutionOutcome outcome
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bloodstone_combat_events (
                    event_id, killer_id, victim_id, killer_current_rampage,
                    killer_best_rampage, new_player_best, killer_guild_current_rampage,
                    killer_guild_best_rampage, new_guild_best, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, resolution.eventId());
            statement.setObject(2, resolution.killerId());
            statement.setObject(3, resolution.victimId());
            statement.setInt(4, outcome.killerCurrentRampage());
            statement.setInt(5, outcome.killerBestRampage());
            statement.setBoolean(6, outcome.newPlayerBest());
            setNullableInteger(statement, 7, outcome.killerGuildCurrentRampage());
            setNullableInteger(statement, 8, outcome.killerGuildBestRampage());
            statement.setBoolean(9, outcome.newGuildBest());
            setInstant(statement, 10, resolution.occurredAt());
            statement.executeUpdate();
        }
    }

    private void lockPlayers(Connection connection, Set<UUID> playerIds) throws SQLException {
        UUID[] orderedIds = playerIds.stream()
                .sorted()
                .toArray(UUID[]::new);
        Array idArray = connection.createArrayOf("uuid", orderedIds);
        try (PreparedStatement statement = connection.prepareStatement("""
                     SELECT player_id
                     FROM bloodstone_players
                     WHERE player_id = ANY (?)
                     ORDER BY player_id
                     FOR UPDATE
                     """)) {
            statement.setArray(1, idArray);
            int found = 0;
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    found++;
                }
            }
            if (found != orderedIds.length) {
                throw new SQLException("A credited Bloodstone player has not been loaded");
            }
        } finally {
            idArray.free();
        }
    }

    private RampageBefore fetchPlayerRampage(
            Connection connection,
            UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT current_rampage, best_rampage
                FROM bloodstone_players
                WHERE player_id = ?
                """)) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Bloodstone player does not exist: " + playerId);
                }
                return new RampageBefore(
                        resultSet.getInt("current_rampage"),
                        resultSet.getInt("best_rampage")
                );
            }
        }
    }

    private RampageBefore fetchGuildRampage(
            Connection connection,
            UUID guildId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT current_rampage, best_rampage
                FROM bloodstone_guild_statistics
                WHERE guild_id = ?
                FOR UPDATE
                """)) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new RampageBefore(0, 0);
                }
                return new RampageBefore(
                        resultSet.getInt("current_rampage"),
                        resultSet.getInt("best_rampage")
                );
            }
        }
    }

    private void updatePlayerStatistics(
            Connection connection,
            Map<UUID, PlayerDelta> deltas
    ) throws SQLException {
        List<Map.Entry<UUID, PlayerDelta>> ordered = deltas.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        String sql = """
                UPDATE bloodstone_players
                SET kills = kills + ?, deaths = deaths + ?, assists = assists + ?,
                    carries = carries + ?, dominations = dominations + ?, revenges = revenges + ?,
                    best_rampage = GREATEST(best_rampage, current_rampage + ?),
                    current_rampage = CASE WHEN ? THEN 0 ELSE current_rampage + ? END,
                    version = version + 1
                WHERE player_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<UUID, PlayerDelta> entry : ordered) {
                PlayerDelta delta = entry.getValue();
                statement.setInt(1, delta.kills);
                statement.setInt(2, delta.deaths);
                statement.setInt(3, delta.assists);
                statement.setInt(4, delta.carries);
                statement.setInt(5, delta.dominations);
                statement.setInt(6, delta.revenges);
                statement.setInt(7, delta.rampageIncrement);
                statement.setBoolean(8, delta.resetRampage);
                statement.setInt(9, delta.rampageIncrement);
                statement.setObject(10, entry.getKey());
                statement.addBatch();
            }
            int[] results = statement.executeBatch();
            for (int result : results) {
                if (result != 1 && result != Statement.SUCCESS_NO_INFO) {
                    throw new SQLException("Failed to update every credited Bloodstone player");
                }
            }
        }
    }

    private void updateGuildStatistics(
            Connection connection,
            Map<UUID, GuildDelta> deltas
    ) throws SQLException {
        List<Map.Entry<UUID, GuildDelta>> ordered = deltas.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        String sql = """
                INSERT INTO bloodstone_guild_statistics (
                    guild_id, kills, deaths, current_rampage, best_rampage, updated_at
                ) VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (guild_id) DO UPDATE SET
                    kills = bloodstone_guild_statistics.kills + EXCLUDED.kills,
                    deaths = bloodstone_guild_statistics.deaths + EXCLUDED.deaths,
                    best_rampage = GREATEST(
                        bloodstone_guild_statistics.best_rampage,
                        CASE WHEN ? THEN ? ELSE
                            bloodstone_guild_statistics.current_rampage + ? END
                    ),
                    current_rampage = CASE WHEN ? THEN ?
                        ELSE bloodstone_guild_statistics.current_rampage + ? END,
                    updated_at = NOW()
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<UUID, GuildDelta> entry : ordered) {
                GuildDelta delta = entry.getValue();
                int insertedCurrent = delta.rampageIncrement;
                statement.setObject(1, entry.getKey());
                statement.setInt(2, delta.kills);
                statement.setInt(3, delta.deaths);
                statement.setInt(4, insertedCurrent);
                statement.setInt(5, delta.rampageIncrement);
                statement.setBoolean(6, delta.resetRampage);
                statement.setInt(7, delta.rampageIncrement);
                statement.setInt(8, delta.rampageIncrement);
                statement.setBoolean(9, delta.resetRampage);
                statement.setInt(10, delta.rampageIncrement);
                statement.setInt(11, delta.rampageIncrement);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public CompletableFuture<ExtraStorageUnlockOutcome> unlockExtraStorage(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            @NonNull Instant now
    ) {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(now, "Purchase time cannot be null");
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement existing = connection.prepareStatement("""
                            SELECT 1
                            FROM bloodstone_extra_storage_purchases
                            WHERE operation_id = ? AND player_id = ?
                            """)) {
                        existing.setObject(1, operationId);
                        existing.setObject(2, playerId);
                        try (ResultSet resultSet = existing.executeQuery()) {
                            if (resultSet.next()) {
                                connection.commit();
                                return new ExtraStorageUnlockOutcome.Unlocked();
                            }
                        }
                    }
                    boolean unlocked;
                    try (PreparedStatement playerLookup = connection.prepareStatement("""
                            SELECT extra_storage_unlocked
                            FROM bloodstone_players
                            WHERE player_id = ?
                            FOR UPDATE
                            """)) {
                        playerLookup.setObject(1, playerId);
                        try (ResultSet resultSet = playerLookup.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new ExtraStorageUnlockOutcome.PlayerNotFound();
                            }
                            unlocked = resultSet.getBoolean("extra_storage_unlocked");
                        }
                    }
                    if (unlocked) {
                        connection.rollback();
                        return new ExtraStorageUnlockOutcome.AlreadyUnlocked();
                    }
                    try (PreparedStatement unlock = connection.prepareStatement("""
                            UPDATE bloodstone_players
                            SET extra_storage_unlocked = TRUE, version = version + 1
                            WHERE player_id = ?
                            """);
                         PreparedStatement purchase = connection.prepareStatement("""
                            INSERT INTO bloodstone_extra_storage_purchases
                                (operation_id, player_id, purchased_at)
                            VALUES (?, ?, ?)
                            """)) {
                        unlock.setObject(1, playerId);
                        unlock.executeUpdate();
                        purchase.setObject(1, operationId);
                        purchase.setObject(2, playerId);
                        setInstant(purchase, 3, now);
                        purchase.executeUpdate();
                    }
                    connection.commit();
                    return new ExtraStorageUnlockOutcome.Unlocked();
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to unlock extra storage for " + playerId, exception);
            }
        });
    }

    @Override
    public CompletableFuture<SoulboundRecovery> reserveSoulboundRecovery(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] itemPayload,
            @NonNull Instant now
    ) {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(itemPayload, "Item payload cannot be null");
        Objects.requireNonNull(now, "Reservation time cannot be null");
        byte[] payload = itemPayload.clone();
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO bloodstone_soulbound_recoveries
                            (operation_id, player_id, item_payload, created_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (operation_id) DO NOTHING
                        """)) {
                    statement.setObject(1, operationId);
                    statement.setObject(2, playerId);
                    statement.setBytes(3, payload);
                    setInstant(statement, 4, now);
                    statement.executeUpdate();
                }
                SoulboundRecovery recovery = findSoulboundRecovery(connection, operationId);
                if (recovery == null || !recovery.playerId().equals(playerId)) {
                    throw new SQLException("Soulbound operation ID belongs to another player");
                }
                return recovery;
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to reserve soulbound recovery " + operationId, exception);
            }
        });
    }

    @Override
    public CompletableFuture<List<SoulboundRecovery>> fetchSoulboundRecoveries(
            @NonNull UUID playerId
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        return databaseExecutor.supply(() -> {
            List<SoulboundRecovery> recoveries = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT operation_id, player_id, item_payload, created_at
                         FROM bloodstone_soulbound_recoveries
                         WHERE player_id = ? AND completed_at IS NULL
                         ORDER BY created_at, operation_id
                         """)) {
                statement.setObject(1, playerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        recoveries.add(mapSoulboundRecovery(resultSet));
                    }
                }
                return List.copyOf(recoveries);
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to fetch soulbound recoveries for " + playerId, exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> completeSoulboundRecovery(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return completeOperation("bloodstone_soulbound_recoveries", operationId, playerId);
    }

    private SoulboundRecovery findSoulboundRecovery(
            Connection connection,
            UUID operationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation_id, player_id, item_payload, created_at
                FROM bloodstone_soulbound_recoveries
                WHERE operation_id = ? AND completed_at IS NULL
                """)) {
            statement.setObject(1, operationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapSoulboundRecovery(resultSet) : null;
            }
        }
    }

    private SoulboundRecovery mapSoulboundRecovery(ResultSet resultSet) throws SQLException {
        return new SoulboundRecovery(
                resultSet.getObject("operation_id", UUID.class),
                resultSet.getObject("player_id", UUID.class),
                resultSet.getBytes("item_payload"),
                getInstant(resultSet, "created_at")
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
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(rewardId, "Reward ID cannot be null");
        Objects.requireNonNull(rewardPayload, "Reward payload cannot be null");
        Objects.requireNonNull(now, "Reservation time cannot be null");
        requireNonNegative(maximumFreeUses, "Maximum free uses");
        requireNonNegative(paidBloodCost, "Paid Blood cost");
        byte[] reward = rewardPayload.clone();
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    lockOperation(connection, operationId);
                    CompletedOrRandomBox existing = findRandomBoxOperation(
                            connection, operationId);
                    if (existing != null) {
                        connection.commit();
                        if (!existing.operation().playerId().equals(playerId)) {
                            throw new SQLException(
                                    "Random-box operation ID belongs to another player");
                        }
                        return !existing.completed()
                                ? new RandomBoxReserveOutcome.Reserved(existing.operation())
                                : new RandomBoxReserveOutcome.AlreadyCompleted();
                    }

                    boolean freeUse = reserveFreeRandomBoxUse(
                            connection, playerId, maximumFreeUses, now);
                    if (!freeUse && !paidUseAllowed) {
                        connection.rollback();
                        return new RandomBoxReserveOutcome.PaymentRequired();
                    }
                    int bloodCost = freeUse ? 0 : paidBloodCost;
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO bloodstone_random_box_operations (
                                operation_id, player_id, reward_id, reward_payload, free_use,
                                blood_cost, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        statement.setObject(1, operationId);
                        statement.setObject(2, playerId);
                        statement.setString(3, rewardId);
                        statement.setBytes(4, reward);
                        statement.setBoolean(5, freeUse);
                        statement.setInt(6, bloodCost);
                        setInstant(statement, 7, now);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return new RandomBoxReserveOutcome.Reserved(new RandomBoxOperation(
                            operationId,
                            playerId,
                            rewardId,
                            reward,
                            freeUse,
                            bloodCost,
                            now
                    ));
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to reserve random-box operation " + operationId, exception);
            }
        });
    }

    private boolean reserveFreeRandomBoxUse(
            Connection connection,
            UUID playerId,
            int maximumFreeUses,
            Instant now
    ) throws SQLException {
        if (maximumFreeUses == 0) {
            return false;
        }
        try (PreparedStatement initialize = connection.prepareStatement("""
                INSERT INTO bloodstone_random_box_usage (player_id, window_start, free_used)
                VALUES (?, NULL, 0)
                ON CONFLICT (player_id) DO NOTHING
                """)) {
            initialize.setObject(1, playerId);
            initialize.executeUpdate();
        }
        Instant windowStart = null;
        int freeUsed = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT window_start, free_used
                FROM bloodstone_random_box_usage
                WHERE player_id = ?
                FOR UPDATE
                """)) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Random Box usage row could not be initialized");
                }
                windowStart = getInstant(resultSet, "window_start");
                freeUsed = resultSet.getInt("free_used");
            }
        }
        RandomBoxWindow.Reservation reservation =
                new RandomBoxWindow(windowStart, freeUsed).reserve(maximumFreeUses, now);
        if (!reservation.freeUse()) {
            return false;
        }
        RandomBoxWindow updatedWindow = reservation.updatedWindow();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE bloodstone_random_box_usage
                SET window_start = ?, free_used = ?
                WHERE player_id = ?
                """)) {
            setInstant(statement, 1, updatedWindow.windowStart());
            statement.setInt(2, updatedWindow.freeUses());
            statement.setObject(3, playerId);
            statement.executeUpdate();
        }
        return true;
    }

    @Override
    public CompletableFuture<List<RandomBoxOperation>> fetchRandomBoxRecoveries(
            @NonNull UUID playerId
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        return databaseExecutor.supply(() -> {
            List<RandomBoxOperation> operations = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT operation_id, player_id, reward_id, reward_payload, free_use,
                                blood_cost, created_at
                         FROM bloodstone_random_box_operations
                         WHERE player_id = ? AND completed_at IS NULL
                         ORDER BY created_at, operation_id
                         """)) {
                statement.setObject(1, playerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        operations.add(mapRandomBoxOperation(resultSet));
                    }
                }
                return List.copyOf(operations);
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to fetch random-box recoveries for " + playerId, exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> completeRandomBox(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return completeOperation("bloodstone_random_box_operations", operationId, playerId);
    }

    private CompletedOrRandomBox findRandomBoxOperation(
            Connection connection,
            UUID operationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation_id, player_id, reward_id, reward_payload, free_use,
                       blood_cost, created_at, completed_at
                FROM bloodstone_random_box_operations
                WHERE operation_id = ?
                """)) {
            statement.setObject(1, operationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new CompletedOrRandomBox(
                        mapRandomBoxOperation(resultSet),
                        getInstant(resultSet, "completed_at") != null
                );
            }
        }
    }

    private RandomBoxOperation mapRandomBoxOperation(ResultSet resultSet) throws SQLException {
        return new RandomBoxOperation(
                resultSet.getObject("operation_id", UUID.class),
                resultSet.getObject("player_id", UUID.class),
                resultSet.getString("reward_id"),
                resultSet.getBytes("reward_payload"),
                resultSet.getBoolean("free_use"),
                resultSet.getInt("blood_cost"),
                getInstant(resultSet, "created_at")
        );
    }

    private boolean claimEnchanterOffer(
            Connection connection,
            UUID playerId,
            String offerKey,
            Instant now,
            Duration cooldown
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bloodstone_enchanter_offer_cooldowns
                    (player_id, offer_key, available_at)
                VALUES (?, ?, ?)
                ON CONFLICT (player_id, offer_key) DO UPDATE
                SET available_at = EXCLUDED.available_at
                WHERE bloodstone_enchanter_offer_cooldowns.available_at <= ?
                RETURNING available_at
                """)) {
            statement.setObject(1, playerId);
            statement.setString(2, offerKey);
            setInstant(statement, 3, now.plus(cooldown));
            setInstant(statement, 4, now);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
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
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        validateOfferClaim(playerId, offerKey, now, cooldown);
        Objects.requireNonNull(originalItemPayload, "Original item payload cannot be null");
        byte[] original = originalItemPayload.clone();
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    lockOperation(connection, operationId);
                    EnchanterOperation existing = findEnchanterOperation(
                            connection, operationId, false);
                    if (existing != null) {
                        connection.commit();
                        if (!existing.playerId().equals(playerId)) {
                            throw new SQLException(
                                    "Enchanter operation ID belongs to another player");
                        }
                        return new EnchanterReserveOutcome.Reserved(existing);
                    }
                    if (!claimEnchanterOffer(
                            connection, playerId, offerKey, now, cooldown)) {
                        Instant availableAt = fetchEnchanterAvailableAt(
                                connection, playerId, offerKey);
                        connection.rollback();
                        return new EnchanterReserveOutcome.OnCooldown(availableAt);
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO bloodstone_enchanter_operations (
                                operation_id, player_id, original_item_payload, state, created_at
                            ) VALUES (?, ?, ?, 'RESERVED', ?)
                            """)) {
                        statement.setObject(1, operationId);
                        statement.setObject(2, playerId);
                        statement.setBytes(3, original);
                        setInstant(statement, 4, now);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return new EnchanterReserveOutcome.Reserved(new EnchanterOperation(
                            operationId,
                            playerId,
                            original,
                            null,
                            RecoverableOperationState.RESERVED,
                            now
                    ));
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to reserve enchanter operation " + operationId, exception);
            }
        });
    }

    private Instant fetchEnchanterAvailableAt(
            Connection connection,
            UUID playerId,
            String offerKey
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT available_at
                FROM bloodstone_enchanter_offer_cooldowns
                WHERE player_id = ? AND offer_key = ?
                """)) {
            statement.setObject(1, playerId);
            statement.setString(2, offerKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Enchanter cooldown disappeared");
                }
                return getInstant(resultSet, "available_at");
            }
        }
    }

    @Override
    public CompletableFuture<Boolean> markEnchanterOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] enchantedItemPayload
    ) {
        return markItemOperationReady(
                "bloodstone_enchanter_operations",
                "enchanted_item_payload",
                operationId,
                playerId,
                enchantedItemPayload
        );
    }

    @Override
    public CompletableFuture<List<EnchanterOperation>> fetchEnchanterRecoveries(
            @NonNull UUID playerId
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        return databaseExecutor.supply(() -> {
            List<EnchanterOperation> operations = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT operation_id, player_id, original_item_payload,
                                enchanted_item_payload, state, created_at
                         FROM bloodstone_enchanter_operations
                         WHERE player_id = ? AND completed_at IS NULL
                         ORDER BY created_at, operation_id
                         """)) {
                statement.setObject(1, playerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        operations.add(mapEnchanterOperation(resultSet));
                    }
                }
                return List.copyOf(operations);
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to fetch enchanter recoveries for " + playerId, exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> completeEnchanterOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return completeOperation("bloodstone_enchanter_operations", operationId, playerId);
    }

    private EnchanterOperation findEnchanterOperation(
            Connection connection,
            UUID operationId,
            boolean pendingOnly
    ) throws SQLException {
        String sql = """
                SELECT operation_id, player_id, original_item_payload,
                       enchanted_item_payload, state, created_at
                FROM bloodstone_enchanter_operations
                WHERE operation_id = ?
                """ + (pendingOnly ? " AND completed_at IS NULL" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, operationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapEnchanterOperation(resultSet) : null;
            }
        }
    }

    private EnchanterOperation mapEnchanterOperation(ResultSet resultSet) throws SQLException {
        return new EnchanterOperation(
                resultSet.getObject("operation_id", UUID.class),
                resultSet.getObject("player_id", UUID.class),
                resultSet.getBytes("original_item_payload"),
                resultSet.getBytes("enchanted_item_payload"),
                RecoverableOperationState.valueOf(resultSet.getString("state")),
                getInstant(resultSet, "created_at")
        );
    }

    @Override
    public CompletableFuture<RepairReserveOutcome> reserveRepairOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] originalItemPayload,
            @NonNull Instant now
    ) {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(originalItemPayload, "Original item payload cannot be null");
        Objects.requireNonNull(now, "Reservation time cannot be null");
        byte[] original = originalItemPayload.clone();
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    lockOperation(connection, operationId);
                    RepairOperation existing = findRepairOperation(connection, operationId);
                    if (existing != null) {
                        connection.commit();
                        if (!existing.playerId().equals(playerId)) {
                            throw new SQLException("Repair operation ID belongs to another player");
                        }
                        return new RepairReserveOutcome.Reserved(existing);
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO bloodstone_repair_operations (
                                operation_id, player_id, original_item_payload, state, created_at
                            ) VALUES (?, ?, ?, 'RESERVED', ?)
                            """)) {
                        statement.setObject(1, operationId);
                        statement.setObject(2, playerId);
                        statement.setBytes(3, original);
                        setInstant(statement, 4, now);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return new RepairReserveOutcome.Reserved(new RepairOperation(
                            operationId,
                            playerId,
                            original,
                            null,
                            RecoverableOperationState.RESERVED,
                            now
                    ));
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to reserve repair operation " + operationId, exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> markRepairOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] repairedItemPayload
    ) {
        return markItemOperationReady(
                "bloodstone_repair_operations",
                "repaired_item_payload",
                operationId,
                playerId,
                repairedItemPayload
        );
    }

    @Override
    public CompletableFuture<List<RepairOperation>> fetchRepairRecoveries(
            @NonNull UUID playerId
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        return databaseExecutor.supply(() -> {
            List<RepairOperation> operations = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT operation_id, player_id, original_item_payload,
                                repaired_item_payload, state, created_at
                         FROM bloodstone_repair_operations
                         WHERE player_id = ? AND completed_at IS NULL
                         ORDER BY created_at, operation_id
                         """)) {
                statement.setObject(1, playerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        operations.add(mapRepairOperation(resultSet));
                    }
                }
                return List.copyOf(operations);
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to fetch repair recoveries for " + playerId, exception);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> completeRepairOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return completeOperation("bloodstone_repair_operations", operationId, playerId);
    }

    private RepairOperation findRepairOperation(
            Connection connection,
            UUID operationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation_id, player_id, original_item_payload,
                       repaired_item_payload, state, created_at
                FROM bloodstone_repair_operations
                WHERE operation_id = ?
                """)) {
            statement.setObject(1, operationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRepairOperation(resultSet) : null;
            }
        }
    }

    private RepairOperation mapRepairOperation(ResultSet resultSet) throws SQLException {
        return new RepairOperation(
                resultSet.getObject("operation_id", UUID.class),
                resultSet.getObject("player_id", UUID.class),
                resultSet.getBytes("original_item_payload"),
                resultSet.getBytes("repaired_item_payload"),
                RecoverableOperationState.valueOf(resultSet.getString("state")),
                getInstant(resultSet, "created_at")
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
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(storageType, "Storage type cannot be null");
        Objects.requireNonNull(sessionToken, "Session token cannot be null");
        Objects.requireNonNull(now, "Open time cannot be null");
        requirePositive(leaseDuration, "Storage lease duration");
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Boolean extraUnlocked = findExtraStorageUnlock(connection, playerId);
                    if (extraUnlocked == null) {
                        connection.rollback();
                        return new StorageOpenOutcome.PlayerNotFound();
                    }
                    if (storageType == StorageType.EXTRA && !extraUnlocked) {
                        connection.rollback();
                        return new StorageOpenOutcome.Locked();
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO bloodstone_storage_contents (player_id, storage_type)
                            VALUES (?, ?)
                            ON CONFLICT (player_id, storage_type) DO NOTHING
                            """)) {
                        statement.setObject(1, playerId);
                        statement.setString(2, storageType.name());
                        statement.executeUpdate();
                    }

                    Instant leaseExpiresAt = now.plus(leaseDuration);
                    StorageSession session = null;
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE bloodstone_storage_contents
                            SET session_token = ?, lease_expires_at = ?
                            WHERE player_id = ? AND storage_type = ?
                              AND (
                                  session_token IS NULL
                                  OR lease_expires_at <= ?
                                  OR session_token = ?
                              )
                            RETURNING contents_payload, version
                            """)) {
                        statement.setObject(1, sessionToken);
                        setInstant(statement, 2, leaseExpiresAt);
                        statement.setObject(3, playerId);
                        statement.setString(4, storageType.name());
                        setInstant(statement, 5, now);
                        statement.setObject(6, sessionToken);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                session = new StorageSession(
                                        playerId,
                                        storageType,
                                        sessionToken,
                                        resultSet.getBytes("contents_payload"),
                                        resultSet.getLong("version"),
                                        leaseExpiresAt
                                );
                            }
                        }
                    }
                    if (session != null) {
                        connection.commit();
                        ownedStorageSessions.add(sessionToken);
                        return new StorageOpenOutcome.Opened(session);
                    }
                    Instant activeLease;
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT lease_expires_at
                            FROM bloodstone_storage_contents
                            WHERE player_id = ? AND storage_type = ?
                            """)) {
                        statement.setObject(1, playerId);
                        statement.setString(2, storageType.name());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                throw new SQLException("Storage row disappeared during open");
                            }
                            activeLease = getInstant(resultSet, "lease_expires_at");
                        }
                    }
                    connection.commit();
                    return new StorageOpenOutcome.InUse(activeLease);
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to open " + storageType + " storage for " + playerId, exception);
            }
        });
    }

    @Override
    public CompletableFuture<StorageWriteOutcome> checkpointStorage(
            @NonNull StorageSession session,
            byte[] contentsPayload,
            @NonNull Instant now,
            @NonNull Duration leaseDuration
    ) {
        Objects.requireNonNull(session, "Storage session cannot be null");
        Objects.requireNonNull(now, "Checkpoint time cannot be null");
        requirePositive(leaseDuration, "Storage lease duration");
        byte[] payload = copy(contentsPayload);
        return databaseExecutor.supply(() -> {
            Instant leaseExpiresAt = now.plus(leaseDuration);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         UPDATE bloodstone_storage_contents
                         SET contents_payload = ?, version = version + 1,
                             lease_expires_at = ?, updated_at = NOW()
                         WHERE player_id = ? AND storage_type = ?
                           AND session_token = ? AND version = ?
                         RETURNING version
                         """)) {
                statement.setBytes(1, payload);
                setInstant(statement, 2, leaseExpiresAt);
                statement.setObject(3, session.playerId());
                statement.setString(4, session.storageType().name());
                statement.setObject(5, session.sessionToken());
                statement.setLong(6, session.version());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        try (PreparedStatement completedLookup = connection.prepareStatement("""
                                SELECT version, lease_expires_at
                                FROM bloodstone_storage_contents
                                WHERE player_id = ? AND storage_type = ?
                                  AND session_token = ?
                                  AND version = ?
                                  AND contents_payload IS NOT DISTINCT FROM ?
                                """)) {
                            completedLookup.setObject(1, session.playerId());
                            completedLookup.setString(2, session.storageType().name());
                            completedLookup.setObject(3, session.sessionToken());
                            completedLookup.setLong(4, session.version() + 1);
                            completedLookup.setBytes(5, payload);
                            try (ResultSet completedResult = completedLookup.executeQuery()) {
                                if (!completedResult.next()) {
                                    return new StorageWriteOutcome.SessionConflict();
                                }
                                return new StorageWriteOutcome.Saved(new StorageSession(
                                        session.playerId(),
                                        session.storageType(),
                                        session.sessionToken(),
                                        payload,
                                        completedResult.getLong("version"),
                                        getInstant(completedResult, "lease_expires_at")
                                ));
                            }
                        }
                    }
                    return new StorageWriteOutcome.Saved(new StorageSession(
                            session.playerId(),
                            session.storageType(),
                            session.sessionToken(),
                            payload,
                            resultSet.getLong("version"),
                            leaseExpiresAt
                    ));
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to checkpoint storage session " + session.sessionToken(),
                        exception
                );
            }
        });
    }

    @Override
    public CompletableFuture<StorageWriteOutcome> closeStorage(
            @NonNull StorageSession session,
            byte[] contentsPayload,
            @NonNull Instant now
    ) {
        Objects.requireNonNull(session, "Storage session cannot be null");
        Objects.requireNonNull(now, "Close time cannot be null");
        byte[] payload = copy(contentsPayload);
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         UPDATE bloodstone_storage_contents
                         SET contents_payload = ?, version = version + 1,
                             session_token = NULL, lease_expires_at = NULL, updated_at = NOW()
                         WHERE player_id = ? AND storage_type = ?
                           AND session_token = ? AND version = ?
                         RETURNING version
                         """)) {
                statement.setBytes(1, payload);
                statement.setObject(2, session.playerId());
                statement.setString(3, session.storageType().name());
                statement.setObject(4, session.sessionToken());
                statement.setLong(5, session.version());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        try (PreparedStatement completedLookup = connection.prepareStatement("""
                                SELECT version
                                FROM bloodstone_storage_contents
                                WHERE player_id = ? AND storage_type = ?
                                  AND session_token IS NULL
                                  AND version = ?
                                  AND contents_payload IS NOT DISTINCT FROM ?
                                """)) {
                            completedLookup.setObject(1, session.playerId());
                            completedLookup.setString(2, session.storageType().name());
                            completedLookup.setLong(3, session.version() + 1);
                            completedLookup.setBytes(4, payload);
                            try (ResultSet completedResult = completedLookup.executeQuery()) {
                                if (!completedResult.next()) {
                                    return new StorageWriteOutcome.SessionConflict();
                                }
                                ownedStorageSessions.remove(session.sessionToken());
                                return new StorageWriteOutcome.Saved(new StorageSession(
                                        session.playerId(),
                                        session.storageType(),
                                        session.sessionToken(),
                                        payload,
                                        completedResult.getLong("version"),
                                        now
                                ));
                            }
                        }
                    }
                    ownedStorageSessions.remove(session.sessionToken());
                    return new StorageWriteOutcome.Saved(new StorageSession(
                            session.playerId(),
                            session.storageType(),
                            session.sessionToken(),
                            payload,
                            resultSet.getLong("version"),
                            now
                    ));
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to close storage session " + session.sessionToken(), exception);
            }
        });
    }

    private Boolean findExtraStorageUnlock(Connection connection, UUID playerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT extra_storage_unlocked
                FROM bloodstone_players
                WHERE player_id = ?
                """)) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? resultSet.getBoolean("extra_storage_unlocked")
                        : null;
            }
        }
    }

    @Override
    public CompletableFuture<List<PlayerLeaderboardEntry>> fetchPlayerLeaderboard(
            @NonNull LeaderboardMetric metric
    ) {
        Objects.requireNonNull(metric, "Leaderboard metric cannot be null");
        String column = leaderboardColumn(metric);
        return databaseExecutor.supply(() -> {
            List<PlayerLeaderboardEntry> entries = new ArrayList<>(
                    LeaderboardSnapshot.MAXIMUM_ENTRIES
            );
            String sql = "SELECT player_id, username, " + column
                    + " AS value FROM bloodstone_players"
                    + " ORDER BY " + column + " DESC, player_id LIMIT ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, LeaderboardSnapshot.MAXIMUM_ENTRIES);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entries.add(new PlayerLeaderboardEntry(
                                resultSet.getObject("player_id", UUID.class),
                                resultSet.getString("username"),
                                resultSet.getLong("value")
                        ));
                    }
                }
                return List.copyOf(entries);
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to fetch player " + metric + " leaderboard", exception);
            }
        });
    }

    @Override
    public CompletableFuture<List<GuildLeaderboardEntry>> fetchGuildLeaderboard(
            @NonNull LeaderboardMetric metric
    ) {
        Objects.requireNonNull(metric, "Leaderboard metric cannot be null");
        String column = leaderboardColumn(metric);
        return databaseExecutor.supply(() -> {
            List<GuildLeaderboardEntry> entries = new ArrayList<>(
                    LeaderboardSnapshot.MAXIMUM_ENTRIES
            );
            String sql = "SELECT guild_id, " + column
                    + " AS value FROM bloodstone_guild_statistics"
                    + " ORDER BY " + column + " DESC, guild_id LIMIT ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, LeaderboardSnapshot.MAXIMUM_ENTRIES);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entries.add(new GuildLeaderboardEntry(
                                resultSet.getObject("guild_id", UUID.class),
                                resultSet.getLong("value")
                        ));
                    }
                }
                return List.copyOf(entries);
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to fetch guild " + metric + " leaderboard", exception);
            }
        });
    }

    private String leaderboardColumn(LeaderboardMetric metric) {
        return switch (metric) {
            case KILLS -> "kills";
            case CURRENT_RAMPAGE -> "current_rampage";
            case BEST_RAMPAGE -> "best_rampage";
        };
    }

    private CompletableFuture<Boolean> markItemOperationReady(
            String table,
            String resultColumn,
            UUID operationId,
            UUID playerId,
            byte[] resultPayload
    ) {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(resultPayload, "Result item payload cannot be null");
        validateOperationTable(table);
        if (!resultColumn.equals("enchanted_item_payload")
                && !resultColumn.equals("repaired_item_payload")) {
            throw new IllegalArgumentException("Unsupported operation result column");
        }
        byte[] payload = resultPayload.clone();
        return databaseExecutor.supply(() -> {
            String updateSql = "UPDATE " + table + " SET " + resultColumn
                    + " = ?, state = 'READY'"
                    + " WHERE operation_id = ? AND player_id = ?"
                    + " AND state = 'RESERVED' AND completed_at IS NULL";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(updateSql)) {
                statement.setBytes(1, payload);
                statement.setObject(2, operationId);
                statement.setObject(3, playerId);
                if (statement.executeUpdate() == 1) {
                    return true;
                }
                String lookupSql = "SELECT 1 FROM " + table
                        + " WHERE operation_id = ? AND player_id = ?"
                        + " AND state = 'READY' AND completed_at IS NULL";
                try (PreparedStatement lookup = connection.prepareStatement(lookupSql)) {
                    lookup.setObject(1, operationId);
                    lookup.setObject(2, playerId);
                    try (ResultSet resultSet = lookup.executeQuery()) {
                        return resultSet.next();
                    }
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to mark item operation ready " + operationId, exception);
            }
        });
    }

    private CompletableFuture<Boolean> completeOperation(
            String table,
            UUID operationId,
            UUID playerId
    ) {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        validateOperationTable(table);
        return databaseExecutor.supply(() -> {
            String completionSql = "UPDATE " + table
                    + " SET completed_at = COALESCE(completed_at, NOW())"
                    + " WHERE operation_id = ? AND player_id = ? RETURNING 1";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(completionSql)) {
                statement.setObject(1, operationId);
                statement.setObject(2, playerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to complete recovery operation " + operationId,
                        exception
                );
            }
        });
    }

    private boolean playerExists(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM bloodstone_players WHERE player_id = ?
                """)) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private PlayerData mapPlayerData(ResultSet resultSet) throws SQLException {
        PlayerProfile profile = new PlayerProfile(
                resultSet.getObject("player_id", UUID.class),
                resultSet.getString("username"),
                resultSet.getInt("kills"),
                resultSet.getInt("deaths"),
                resultSet.getInt("assists"),
                resultSet.getInt("carries"),
                resultSet.getInt("dominations"),
                resultSet.getInt("revenges"),
                resultSet.getInt("current_rampage"),
                resultSet.getInt("best_rampage"),
                resultSet.getBoolean("extra_storage_unlocked"),
                resultSet.getLong("version")
        );
        return new PlayerData(profile);
    }

    private void lockOperation(Connection connection, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))
                """)) {
            statement.setObject(1, operationId);
            statement.executeQuery();
        }
    }

    private boolean isRetryableTransactionFailure(SQLException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if ("40001".equals(sqlState) || "40P01".equals(sqlState)) {
                    return true;
                }
                SQLException next = sqlException.getNextException();
                if (next != null && next != sqlException
                        && isRetryableTransactionFailure(next)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void releaseOwnedStorageSessions() {
        if (ownedStorageSessions.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE bloodstone_storage_contents
                     SET session_token = NULL, lease_expires_at = NULL
                     WHERE session_token = ?
                     """)) {
            for (UUID sessionToken : Set.copyOf(ownedStorageSessions)) {
                statement.setObject(1, sessionToken);
                statement.addBatch();
            }
            statement.executeBatch();
            ownedStorageSessions.clear();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to release Bloodstone storage sessions", exception);
        }
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
            releaseOwnedStorageSessions();
        } catch (RuntimeException exception) {
            releaseFailure = exception;
        } finally {
            dataSource.close();
        }
        if (releaseFailure != null) {
            throw releaseFailure;
        }
    }

    private static void validateOperationTable(String table) {
        if (!table.equals("bloodstone_soulbound_recoveries")
                && !table.equals("bloodstone_random_box_operations")
                && !table.equals("bloodstone_enchanter_operations")
                && !table.equals("bloodstone_repair_operations")) {
            throw new IllegalArgumentException("Unsupported recovery operation table");
        }
    }

    private static void validateUsername(String username) {
        Objects.requireNonNull(username, "Username cannot be null");
        if (username.isBlank() || username.length() > 16) {
            throw new IllegalArgumentException(
                    "Username must contain between 1 and 16 characters");
        }
    }

    private static void validateOfferClaim(
            UUID playerId,
            String offerKey,
            Instant now,
            Duration cooldown
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        validateKey(offerKey, "Offer key");
        Objects.requireNonNull(now, "Claim time cannot be null");
        requirePositive(cooldown, "Enchanter offer cooldown");
    }

    private static void validateKey(String value, String description) {
        Objects.requireNonNull(value, description + " cannot be null");
        if (value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(
                    description + " must contain between 1 and 128 characters");
        }
    }

    private static void requireNonNegative(long value, String description) {
        if (value < 0) {
            throw new IllegalArgumentException(description + " cannot be negative");
        }
    }

    private static void requirePositive(Duration duration, String description) {
        Objects.requireNonNull(duration, description + " cannot be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(description + " must be positive");
        }
    }

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static byte @Nullable [] copy(byte @Nullable [] value) {
        return value == null ? null : value.clone();
    }

    private static @Nullable Instant getInstant(ResultSet resultSet, String column)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void setInstant(
            PreparedStatement statement,
            int index,
            @Nullable Instant instant
    ) throws SQLException {
        statement.setTimestamp(index, instant == null ? null : Timestamp.from(instant));
    }

    private static @Nullable Integer getNullableInteger(ResultSet resultSet, String column)
            throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static @Nullable Long getNullableLong(ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static void setNullableInteger(
            PreparedStatement statement,
            int index,
            @Nullable Integer value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private record RampageBefore(int current, int best) {
    }

    private record CompletedOrRandomBox(
            RandomBoxOperation operation,
            boolean completed
    ) {
    }

    private static final class PlayerDelta {
        private int kills;
        private int deaths;
        private int assists;
        private int carries;
        private int dominations;
        private int revenges;
        private int rampageIncrement;
        private boolean resetRampage;

        private PlayerDelta creditKill(boolean domination, boolean revenge) {
            kills++;
            rampageIncrement++;
            dominations += domination ? 1 : 0;
            revenges += revenge ? 1 : 0;
            return this;
        }

        private PlayerDelta creditDeath() {
            deaths++;
            resetRampage = true;
            return this;
        }

        private PlayerDelta creditAssist() {
            assists++;
            return this;
        }

        private PlayerDelta creditCarry() {
            carries++;
            return this;
        }
    }

    private static final class GuildDelta {
        private int kills;
        private int deaths;
        private int rampageIncrement;
        private boolean resetRampage;

        private GuildDelta creditKill() {
            kills++;
            rampageIncrement++;
            return this;
        }

        private GuildDelta creditDeath() {
            deaths++;
            resetRampage = true;
            return this;
        }
    }
}
