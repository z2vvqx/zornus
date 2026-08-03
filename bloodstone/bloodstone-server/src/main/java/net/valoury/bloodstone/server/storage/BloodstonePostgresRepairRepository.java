package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.model.RecoverableOperationState;
import net.valoury.bloodstone.server.model.RepairOperation;
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

final class BloodstonePostgresRepairRepository {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;
    private final BloodstonePostgresOperationStatements operationStatements;

    BloodstonePostgresRepairRepository(
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
                    operationStatements.lockOperation(connection, operationId);
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

    public CompletableFuture<Boolean> markRepairOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] repairedItemPayload
    ) {
        return operationStatements.markItemOperationReady(
                "bloodstone_repair_operations",
                "repaired_item_payload",
                operationId,
                playerId,
                repairedItemPayload
        );
    }

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

    public CompletableFuture<Boolean> completeRepairOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return operationStatements.completeOperation("bloodstone_repair_operations", operationId, playerId);
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

}
