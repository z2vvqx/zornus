package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.model.CombatResolution;
import net.valoury.bloodstone.server.model.RampageTransition;
import net.valoury.shared.database.DatabaseExecutor;
import org.jspecify.annotations.Nullable;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class BloodstonePostgresCombatRepository {

    private static final int TRANSACTION_MAXIMUM_ATTEMPTS = 3;

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    BloodstonePostgresCombatRepository(
            HikariDataSource dataSource,
            DatabaseExecutor databaseExecutor
    ) {
        this.dataSource = dataSource;
        this.databaseExecutor = databaseExecutor;
    }

    CompletableFuture<CombatResolutionOutcome> resolve(
            CombatResolution resolution
    ) {
        return databaseExecutor.supply(() -> executeWithRetry(resolution));
    }

    CompletableFuture<Boolean> recordDeath(
            UUID eventId,
            UUID victimId,
            @Nullable UUID victimGuildId,
            Instant occurredAt
    ) {
        return databaseExecutor.supply(() -> executeDeathWithRetry(
                eventId,
                victimId,
                victimGuildId,
                occurredAt
        ));
    }

    private CombatResolutionOutcome executeWithRetry(
            CombatResolution resolution
    ) {
        SQLException lastFailure = null;
        for (int attempt = 1;
             attempt <= TRANSACTION_MAXIMUM_ATTEMPTS;
             attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setTransactionIsolation(
                        Connection.TRANSACTION_SERIALIZABLE
                );
                connection.setAutoCommit(false);
                try {
                    CombatResolutionOutcome result = execute(
                            connection,
                            resolution
                    );
                    connection.commit();
                    return result;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    if (!isRetryableTransactionFailure(exception)
                            || attempt == TRANSACTION_MAXIMUM_ATTEMPTS) {
                        throw exception;
                    }
                    lastFailure = exception;
                }
            } catch (SQLException exception) {
                if (!isRetryableTransactionFailure(exception)
                        || attempt == TRANSACTION_MAXIMUM_ATTEMPTS) {
                    throw new RuntimeException(
                            "Failed to resolve Bloodstone combat event "
                                    + resolution.eventId(),
                            exception
                    );
                }
                lastFailure = exception;
            }
        }
        throw new RuntimeException(
                "Failed to resolve Bloodstone combat event "
                        + resolution.eventId(),
                lastFailure
        );
    }

    private CombatResolutionOutcome execute(
            Connection connection,
            CombatResolution resolution
    ) throws SQLException {
        lockOperation(connection, resolution.eventId());
        CombatResolutionOutcome existing = findOutcome(
                connection,
                resolution.eventId()
        );
        if (existing != null) {
            return existing;
        }

        Set<UUID> creditedPlayers = new HashSet<>(
                resolution.assistPlayerIds()
        );
        creditedPlayers.add(resolution.killerId());
        creditedPlayers.add(resolution.victimId());
        if (resolution.carryPlayerId() != null) {
            creditedPlayers.add(resolution.carryPlayerId());
        }
        lockPlayers(connection, creditedPlayers);

        RampageBefore playerBefore = fetchPlayerRampage(
                connection,
                resolution.killerId()
        );
        Map<UUID, PlayerDelta> playerDeltas = new HashMap<>();
        playerDeltas.computeIfAbsent(
                        resolution.killerId(),
                        ignored -> new PlayerDelta()
                )
                .creditKill(
                        resolution.domination(),
                        resolution.revenge()
                );
        playerDeltas.computeIfAbsent(
                        resolution.victimId(),
                        ignored -> new PlayerDelta()
                )
                .creditDeath();
        for (UUID assistPlayerId : resolution.assistPlayerIds()) {
            playerDeltas.computeIfAbsent(
                            assistPlayerId,
                            ignored -> new PlayerDelta()
                    )
                    .creditAssist();
        }
        if (resolution.carryPlayerId() != null) {
            playerDeltas.computeIfAbsent(
                            resolution.carryPlayerId(),
                            ignored -> new PlayerDelta()
                    )
                    .creditCarry();
        }
        updatePlayerStatistics(connection, playerDeltas);

        RampageTransition playerTransition = RampageTransition.afterKill(
                playerBefore.current(),
                playerBefore.best()
        );
        Integer killerGuildCurrentRampage = null;
        Integer killerGuildBestRampage = null;
        boolean newGuildBest = false;
        if (resolution.killerGuildId() != null) {
            Map<UUID, GuildDelta> guildDeltas = new HashMap<>();
            guildDeltas.computeIfAbsent(
                            resolution.killerGuildId(),
                            ignored -> new GuildDelta()
                    )
                    .creditKill();
            if (resolution.victimGuildId() != null) {
                guildDeltas.computeIfAbsent(
                                resolution.victimGuildId(),
                                ignored -> new GuildDelta()
                        )
                        .creditDeath();
            }
            RampageBefore guildBefore = fetchGuildRampage(
                    connection,
                    resolution.killerGuildId()
            );
            updateGuildStatistics(connection, guildDeltas);
            boolean sameGuildReset = resolution.killerGuildId().equals(
                    resolution.victimGuildId()
            );
            RampageTransition guildTransition = sameGuildReset
                    ? RampageTransition.afterDeathThenKill(
                            guildBefore.best()
                    )
                    : RampageTransition.afterKill(
                            guildBefore.current(),
                            guildBefore.best()
                    );
            killerGuildCurrentRampage = guildTransition.current();
            killerGuildBestRampage = guildTransition.best();
            newGuildBest = guildTransition.newBest();
        } else if (resolution.victimGuildId() != null) {
            Map<UUID, GuildDelta> guildDeltas = new HashMap<>();
            guildDeltas.computeIfAbsent(
                            resolution.victimGuildId(),
                            ignored -> new GuildDelta()
                    )
                    .creditDeath();
            updateGuildStatistics(connection, guildDeltas);
        }

        CombatResolutionOutcome outcome = new CombatResolutionOutcome(
                true,
                playerTransition.current(),
                playerTransition.best(),
                playerTransition.newBest(),
                killerGuildCurrentRampage,
                killerGuildBestRampage,
                newGuildBest
        );
        insertOutcome(connection, resolution, outcome);
        return outcome;
    }

    private boolean executeDeathWithRetry(
            UUID eventId,
            UUID victimId,
            @Nullable UUID victimGuildId,
            Instant occurredAt
    ) {
        SQLException lastFailure = null;
        for (int attempt = 1;
             attempt <= TRANSACTION_MAXIMUM_ATTEMPTS;
             attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setTransactionIsolation(
                        Connection.TRANSACTION_SERIALIZABLE
                );
                connection.setAutoCommit(false);
                try {
                    lockOperation(connection, eventId);
                    if (uncreditedDeathExists(connection, eventId)) {
                        connection.commit();
                        return false;
                    }
                    lockPlayers(connection, Set.of(victimId));
                    Map<UUID, PlayerDelta> playerDeltas = new HashMap<>();
                    playerDeltas.computeIfAbsent(
                                    victimId,
                                    ignored -> new PlayerDelta()
                            )
                            .creditDeath();
                    updatePlayerStatistics(connection, playerDeltas);
                    if (victimGuildId != null) {
                        Map<UUID, GuildDelta> guildDeltas = new HashMap<>();
                        guildDeltas.computeIfAbsent(
                                        victimGuildId,
                                        ignored -> new GuildDelta()
                                )
                                .creditDeath();
                        updateGuildStatistics(connection, guildDeltas);
                    }
                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                            INSERT INTO bloodstone_uncredited_death_events
                                (event_id, victim_id, victim_guild_id,
                                 occurred_at)
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
                            || attempt == TRANSACTION_MAXIMUM_ATTEMPTS) {
                        throw exception;
                    }
                    lastFailure = exception;
                }
            } catch (SQLException exception) {
                if (!isRetryableTransactionFailure(exception)
                        || attempt == TRANSACTION_MAXIMUM_ATTEMPTS) {
                    throw new RuntimeException(
                            "Failed to record uncredited death " + eventId,
                            exception
                    );
                }
                lastFailure = exception;
            }
        }
        throw new RuntimeException(
                "Failed to record uncredited death " + eventId,
                lastFailure
        );
    }

    private static boolean uncreditedDeathExists(
            Connection connection,
            UUID eventId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM bloodstone_uncredited_death_events
                WHERE event_id = ?
                """)) {
            statement.setObject(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static CombatResolutionOutcome findOutcome(
            Connection connection,
            UUID eventId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT killer_current_rampage, killer_best_rampage,
                       new_player_best, killer_guild_current_rampage,
                       killer_guild_best_rampage, new_guild_best
                FROM bloodstone_combat_events
                WHERE event_id = ?
                """)) {
            statement.setObject(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new CombatResolutionOutcome(
                        false,
                        resultSet.getInt("killer_current_rampage"),
                        resultSet.getInt("killer_best_rampage"),
                        resultSet.getBoolean("new_player_best"),
                        getNullableInteger(
                                resultSet,
                                "killer_guild_current_rampage"
                        ),
                        getNullableInteger(
                                resultSet,
                                "killer_guild_best_rampage"
                        ),
                        resultSet.getBoolean("new_guild_best")
                );
            }
        }
    }

    private static void insertOutcome(
            Connection connection,
            CombatResolution resolution,
            CombatResolutionOutcome outcome
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bloodstone_combat_events (
                    event_id, killer_id, victim_id,
                    killer_current_rampage, killer_best_rampage,
                    new_player_best, killer_guild_current_rampage,
                    killer_guild_best_rampage, new_guild_best, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, resolution.eventId());
            statement.setObject(2, resolution.killerId());
            statement.setObject(3, resolution.victimId());
            statement.setInt(4, outcome.killerCurrentRampage());
            statement.setInt(5, outcome.killerBestRampage());
            statement.setBoolean(6, outcome.newPlayerBest());
            setNullableInteger(
                    statement,
                    7,
                    outcome.killerGuildCurrentRampage()
            );
            setNullableInteger(
                    statement,
                    8,
                    outcome.killerGuildBestRampage()
            );
            statement.setBoolean(9, outcome.newGuildBest());
            setInstant(statement, 10, resolution.occurredAt());
            statement.executeUpdate();
        }
    }

    private static void lockPlayers(
            Connection connection,
            Set<UUID> playerIds
    ) throws SQLException {
        UUID[] orderedIds = playerIds.stream().sorted().toArray(UUID[]::new);
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
                throw new SQLException(
                        "A credited Bloodstone player has not been loaded"
                );
            }
        } finally {
            idArray.free();
        }
    }

    private static RampageBefore fetchPlayerRampage(
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
                    throw new SQLException(
                            "Bloodstone player does not exist: " + playerId
                    );
                }
                return new RampageBefore(
                        resultSet.getInt("current_rampage"),
                        resultSet.getInt("best_rampage")
                );
            }
        }
    }

    private static RampageBefore fetchGuildRampage(
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

    private static void updatePlayerStatistics(
            Connection connection,
            Map<UUID, PlayerDelta> deltas
    ) throws SQLException {
        List<Map.Entry<UUID, PlayerDelta>> ordered = deltas.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        String sql = """
                UPDATE bloodstone_players
                SET kills = kills + ?, deaths = deaths + ?,
                    assists = assists + ?, carries = carries + ?,
                    dominations = dominations + ?, revenges = revenges + ?,
                    best_rampage = GREATEST(
                        best_rampage,
                        current_rampage + ?
                    ),
                    current_rampage = CASE WHEN ? THEN 0
                        ELSE current_rampage + ? END,
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
                    throw new SQLException(
                            "Failed to update every credited Bloodstone player"
                    );
                }
            }
        }
    }

    private static void updateGuildStatistics(
            Connection connection,
            Map<UUID, GuildDelta> deltas
    ) throws SQLException {
        List<Map.Entry<UUID, GuildDelta>> ordered = deltas.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        String sql = """
                INSERT INTO bloodstone_guild_statistics (
                    guild_id, kills, deaths, current_rampage,
                    best_rampage, updated_at
                ) VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (guild_id) DO UPDATE SET
                    kills = bloodstone_guild_statistics.kills
                        + EXCLUDED.kills,
                    deaths = bloodstone_guild_statistics.deaths
                        + EXCLUDED.deaths,
                    best_rampage = GREATEST(
                        bloodstone_guild_statistics.best_rampage,
                        CASE WHEN ? THEN ? ELSE
                            bloodstone_guild_statistics.current_rampage + ?
                        END
                    ),
                    current_rampage = CASE WHEN ? THEN ? ELSE
                        bloodstone_guild_statistics.current_rampage + ? END,
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

    private static void lockOperation(
            Connection connection,
            UUID operationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))
                """)) {
            statement.setObject(1, operationId);
            statement.executeQuery();
        }
    }

    private static boolean isRetryableTransactionFailure(
            SQLException exception
    ) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLTransientException) {
                return true;
            }
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if ("40001".equals(sqlState) || "40P01".equals(sqlState)) {
                    return true;
                }
                SQLException next = sqlException.getNextException();
                if (next != null
                        && next != sqlException
                        && isRetryableTransactionFailure(next)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static void rollback(
            Connection connection,
            SQLException original
    ) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static @Nullable Integer getNullableInteger(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        int value = resultSet.getInt(column);
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

    private static void setInstant(
            PreparedStatement statement,
            int index,
            @Nullable Instant instant
    ) throws SQLException {
        statement.setTimestamp(
                index,
                instant == null ? null : Timestamp.from(instant)
        );
    }

    private record RampageBefore(int current, int best) {
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

        private PlayerDelta creditKill(
                boolean domination,
                boolean revenge
        ) {
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
