package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.shared.database.DatabaseExecutor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class BloodstonePostgresOperationStatements {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    BloodstonePostgresOperationStatements(
            HikariDataSource dataSource,
            DatabaseExecutor databaseExecutor
    ) {
        this.dataSource = Objects.requireNonNull(
                dataSource,
                "Data source cannot be null"
        );
        this.databaseExecutor = Objects.requireNonNull(
                databaseExecutor,
                "Database executor cannot be null"
        );
    }

    CompletableFuture<Boolean> markItemOperationReady(
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
                && !resultColumn.equals("repaired_item_payload")
                && !resultColumn.equals("fused_axe_payload")) {
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

    CompletableFuture<Boolean> completeOperation(
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

    void lockOperation(Connection connection, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))
                """)) {
            statement.setObject(1, operationId);
            statement.executeQuery();
        }
    }

    private static void validateOperationTable(String table) {
        if (!table.equals("bloodstone_soulbound_recoveries")
                && !table.equals("bloodstone_random_box_operations")
                && !table.equals("bloodstone_enchanter_operations")
                && !table.equals("bloodstone_repair_operations")
                && !table.equals("bloodstone_axe_fuser_operations")) {
            throw new IllegalArgumentException("Unsupported recovery operation table");
        }
    }

}
