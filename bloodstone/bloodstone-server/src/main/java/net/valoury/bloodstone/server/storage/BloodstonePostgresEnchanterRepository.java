package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.model.EnchanterOperation;
import net.valoury.bloodstone.server.model.RecoverableOperationState;
import net.valoury.shared.database.DatabaseExecutor;
import org.jspecify.annotations.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.valoury.bloodstone.server.storage.BloodstonePostgresRepositorySupport.*;

final class BloodstonePostgresEnchanterRepository {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;
    private final BloodstonePostgresOperationStatements operationStatements;

    BloodstonePostgresEnchanterRepository(
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
                    operationStatements.lockOperation(connection, operationId);
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

    public CompletableFuture<Boolean> markEnchanterOperationReady(
            @NonNull UUID operationId,
            @NonNull UUID playerId,
            byte @NonNull [] enchantedItemPayload
    ) {
        return operationStatements.markItemOperationReady(
                "bloodstone_enchanter_operations",
                "enchanted_item_payload",
                operationId,
                playerId,
                enchantedItemPayload
        );
    }

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

    public CompletableFuture<Boolean> completeEnchanterOperation(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return operationStatements.completeOperation("bloodstone_enchanter_operations", operationId, playerId);
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

}
