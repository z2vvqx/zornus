package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.BloodstonePlayerIdentity;
import net.valoury.bloodstone.server.model.PlayerData;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.shared.database.DatabaseExecutor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class BloodstonePostgresPlayerRepository {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    BloodstonePostgresPlayerRepository(
            HikariDataSource dataSource,
            DatabaseExecutor databaseExecutor
    ) {
        this.dataSource = dataSource;
        this.databaseExecutor = databaseExecutor;
    }

    CompletableFuture<PlayerData> loadOrCreate(
            UUID playerId,
            String username
    ) {
        BloodstonePlayerIdentity.requireValidUsername(username);
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                            INSERT INTO bloodstone_players (
                                player_id,
                                username,
                                last_joined_at
                            )
                            VALUES (?, ?, NOW())
                            ON CONFLICT (player_id) DO UPDATE
                            SET username = EXCLUDED.username,
                                last_joined_at = EXCLUDED.last_joined_at
                            """)) {
                        statement.setObject(1, playerId);
                        statement.setString(2, username);
                        statement.executeUpdate();
                    }
                    PlayerData result = fetch(connection, playerId);
                    if (result == null) {
                        throw new SQLException(
                                "Player disappeared while loading: "
                                        + playerId
                        );
                    }
                    connection.commit();
                    return result;
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to load Bloodstone player " + playerId,
                        exception
                );
            }
        });
    }

    CompletableFuture<Optional<PlayerData>> fetch(UUID playerId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return Optional.ofNullable(fetch(connection, playerId));
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to fetch Bloodstone player " + playerId,
                        exception
                );
            }
        });
    }

    private PlayerData fetch(
            Connection connection,
            UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.player_id, p.username, p.kills, p.deaths,
                       p.assists, p.carries, p.dominations, p.revenges,
                       p.current_rampage, p.best_rampage,
                       p.extra_storage_unlocked, p.version
                FROM bloodstone_players p
                WHERE p.player_id = ?
                """)) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? map(resultSet) : null;
            }
        }
    }

    private static PlayerData map(ResultSet resultSet) throws SQLException {
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
}
