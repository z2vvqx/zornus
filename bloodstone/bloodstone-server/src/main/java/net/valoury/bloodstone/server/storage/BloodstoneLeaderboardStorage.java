package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.GuildLeaderboardEntry;
import net.valoury.bloodstone.server.model.LeaderboardMetric;
import net.valoury.bloodstone.server.model.PlayerLeaderboardEntry;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface BloodstoneLeaderboardStorage {

    CompletableFuture<List<PlayerLeaderboardEntry>> fetchPlayerLeaderboard(
            @NonNull LeaderboardMetric metric
    );

    CompletableFuture<List<GuildLeaderboardEntry>> fetchGuildLeaderboard(
            @NonNull LeaderboardMetric metric
    );
}
