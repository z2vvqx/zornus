package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.BloodstonePlayerIdentity;
import net.valoury.bloodstone.server.model.GuildLeaderboardEntry;
import net.valoury.bloodstone.server.model.LeaderboardMetric;
import net.valoury.bloodstone.server.model.LeaderboardSnapshot;
import net.valoury.bloodstone.server.model.PlayerLeaderboardEntry;
import net.valoury.shared.database.DatabaseExecutor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class BloodstonePostgresLeaderboardRepository {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    BloodstonePostgresLeaderboardRepository(
            HikariDataSource dataSource,
            DatabaseExecutor databaseExecutor
    ) {
        this.dataSource = dataSource;
        this.databaseExecutor = databaseExecutor;
    }

    CompletableFuture<List<PlayerLeaderboardEntry>> fetchPlayers(
            LeaderboardMetric metric
    ) {
        String column = column(metric);
        return databaseExecutor.supply(() -> {
            List<PlayerLeaderboardEntry> entries = new ArrayList<>(
                    LeaderboardSnapshot.MAXIMUM_ENTRIES
            );
            String sql = "SELECT player_id, username, " + column
                    + " AS value FROM bloodstone_players"
                    + " WHERE username ~ ?"
                    + " ORDER BY " + column + " DESC, player_id LIMIT ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {
                statement.setString(
                        1,
                        BloodstonePlayerIdentity.VALID_USERNAME_REGEX
                );
                statement.setInt(2, LeaderboardSnapshot.MAXIMUM_ENTRIES);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entries.add(new PlayerLeaderboardEntry(
                                resultSet.getObject(
                                        "player_id",
                                        UUID.class
                                ),
                                resultSet.getString("username"),
                                resultSet.getLong("value")
                        ));
                    }
                }
                return List.copyOf(entries);
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to fetch player " + metric
                                + " leaderboard",
                        exception
                );
            }
        });
    }

    CompletableFuture<List<GuildLeaderboardEntry>> fetchGuilds(
            LeaderboardMetric metric
    ) {
        String column = column(metric);
        return databaseExecutor.supply(() -> {
            List<GuildLeaderboardEntry> entries = new ArrayList<>(
                    LeaderboardSnapshot.MAXIMUM_ENTRIES
            );
            String sql = "SELECT guild_id, " + column
                    + " AS value FROM bloodstone_guild_statistics"
                    + " ORDER BY " + column + " DESC, guild_id LIMIT ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {
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
                        "Failed to fetch guild " + metric
                                + " leaderboard",
                        exception
                );
            }
        });
    }

    private static String column(LeaderboardMetric metric) {
        return switch (metric) {
            case KILLS -> "kills";
            case CURRENT_RAMPAGE -> "current_rampage";
            case BEST_RAMPAGE -> "best_rampage";
        };
    }
}
