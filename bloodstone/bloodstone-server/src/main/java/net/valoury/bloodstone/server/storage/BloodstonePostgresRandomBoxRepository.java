package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.model.RandomBoxWindow;
import net.valoury.shared.database.DatabaseExecutor;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.valoury.bloodstone.server.storage.BloodstonePostgresRepositorySupport.*;

final class BloodstonePostgresRandomBoxRepository {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;
    private final BloodstonePostgresOperationStatements operationStatements;

    BloodstonePostgresRandomBoxRepository(
            HikariDataSource dataSource,
            DatabaseExecutor databaseExecutor,
            BloodstonePostgresOperationStatements operationStatements
    ) {
        this.dataSource = Objects.requireNonNull(
                dataSource,
                "Data source cannot be null"
        );
        this.databaseExecutor = Objects.requireNonNull(
                databaseExecutor,
                "Database executor cannot be null"
        );
        this.operationStatements = Objects.requireNonNull(
                operationStatements,
                "Operation statements cannot be null"
        );
    }

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
                    operationStatements.lockOperation(connection, operationId);
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

    public CompletableFuture<Boolean> completeRandomBox(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return operationStatements.completeOperation("bloodstone_random_box_operations", operationId, playerId);
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

    private record CompletedOrRandomBox(
            RandomBoxOperation operation,
            boolean completed
    ) {
    }

}
