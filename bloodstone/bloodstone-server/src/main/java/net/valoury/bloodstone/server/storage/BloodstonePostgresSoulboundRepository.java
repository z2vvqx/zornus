package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.model.SoulboundRecovery;
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

final class BloodstonePostgresSoulboundRepository {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;
    private final BloodstonePostgresOperationStatements operationStatements;

    BloodstonePostgresSoulboundRepository(
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

    public CompletableFuture<Boolean> completeSoulboundRecovery(
            @NonNull UUID operationId,
            @NonNull UUID playerId
    ) {
        return operationStatements.completeOperation("bloodstone_soulbound_recoveries", operationId, playerId);
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

}
