package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.model.AxeFuserOperation;
import net.valoury.bloodstone.server.model.RecoverableOperationState;
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

final class BloodstonePostgresAxeFuserRepository {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;
    private final BloodstonePostgresOperationStatements operationStatements;

    BloodstonePostgresAxeFuserRepository(
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

    public CompletableFuture<AxeFuserReserveOutcome> reserveAxeFuserOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] originalAxesPayload,
            int bloodAlloyCost,
            @NonNull Instant now
    ) {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(
                originalAxesPayload,
                "Original axes payload cannot be null"
        );
        Objects.requireNonNull(now, "Reservation time cannot be null");
        if (bloodAlloyCost < 1) {
            throw new IllegalArgumentException(
                    "Axe Fuser Blood Alloy cost must be positive"
            );
        }
        byte[] originalAxes = originalAxesPayload.clone();
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    operationStatements.lockOperation(connection, operationId);
                    AxeFuserOperation existing =
                            findAxeFuserOperation(connection, operationId);
                    if (existing != null) {
                        connection.commit();
                        if (!existing.playerId().equals(playerId)) {
                            throw new SQLException(
                                    "Axe Fuser operation ID belongs to another player"
                            );
                        }
                        if (existing.bloodAlloyCost() != bloodAlloyCost) {
                            throw new SQLException(
                                    "Axe Fuser operation cost changed during retry"
                            );
                        }
                        return new AxeFuserReserveOutcome.Reserved(existing);
                    }
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO bloodstone_axe_fuser_operations (
                                operation_id, player_id, original_axes_payload,
                                blood_alloy_cost, state, created_at
                            ) VALUES (?, ?, ?, ?, 'RESERVED', ?)
                            """)) {
                        statement.setObject(1, operationId);
                        statement.setObject(2, playerId);
                        statement.setBytes(3, originalAxes);
                        statement.setInt(4, bloodAlloyCost);
                        setInstant(statement, 5, now);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return new AxeFuserReserveOutcome.Reserved(
                            new AxeFuserOperation(
                                    operationId,
                                    playerId,
                                    originalAxes,
                                    bloodAlloyCost,
                                    null,
                                    RecoverableOperationState.RESERVED,
                                    now
                            )
                    );
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to reserve Axe Fuser operation " + operationId,
                        exception
                );
            }
        });
    }

    public CompletableFuture<Boolean> markAxeFuserOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] fusedAxePayload
    ) {
        return operationStatements.markItemOperationReady(
                "bloodstone_axe_fuser_operations",
                "fused_axe_payload",
                operationId,
                playerId,
                fusedAxePayload
        );
    }

    public CompletableFuture<List<AxeFuserOperation>> fetchAxeFuserRecoveries(
            @NonNull UUID playerId
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        return databaseExecutor.supply(() -> {
            List<AxeFuserOperation> operations = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT operation_id, player_id, original_axes_payload,
                                blood_alloy_cost, fused_axe_payload, state,
                                created_at
                         FROM bloodstone_axe_fuser_operations
                         WHERE player_id = ? AND completed_at IS NULL
                         ORDER BY created_at, operation_id
                         """)) {
                statement.setObject(1, playerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        operations.add(mapAxeFuserOperation(resultSet));
                    }
                }
                return List.copyOf(operations);
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to fetch Axe Fuser recoveries for " + playerId,
                        exception
                );
            }
        });
    }

    public CompletableFuture<Boolean> completeAxeFuserOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return operationStatements.completeOperation(
                "bloodstone_axe_fuser_operations",
                operationId,
                playerId
        );
    }

    private AxeFuserOperation findAxeFuserOperation(
            Connection connection,
            UUID operationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT operation_id, player_id, original_axes_payload,
                       blood_alloy_cost, fused_axe_payload, state, created_at
                FROM bloodstone_axe_fuser_operations
                WHERE operation_id = ?
                """)) {
            statement.setObject(1, operationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? mapAxeFuserOperation(resultSet)
                        : null;
            }
        }
    }

    private AxeFuserOperation mapAxeFuserOperation(ResultSet resultSet)
            throws SQLException {
        return new AxeFuserOperation(
                resultSet.getObject("operation_id", UUID.class),
                resultSet.getObject("player_id", UUID.class),
                resultSet.getBytes("original_axes_payload"),
                resultSet.getInt("blood_alloy_cost"),
                resultSet.getBytes("fused_axe_payload"),
                RecoverableOperationState.valueOf(resultSet.getString("state")),
                getInstant(resultSet, "created_at")
        );
    }

}
