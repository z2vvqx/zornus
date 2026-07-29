package net.valoury.bloodstone.server.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record LeaderboardSnapshot(Instant refreshedAt, Map<LeaderboardBoard, List<String>> entries) {

    public static final int MAXIMUM_ENTRIES = 10;

    public LeaderboardSnapshot {
        EnumMap<LeaderboardBoard, List<String>> copy = new EnumMap<>(LeaderboardBoard.class);
        entries.forEach((board, values) -> copy.put(board, List.copyOf(values)));
        for (LeaderboardBoard board : LeaderboardBoard.values()) {
            copy.putIfAbsent(board, List.of());
        }
        entries = Map.copyOf(copy);
    }

    public static LeaderboardSnapshot empty() {
        return new LeaderboardSnapshot(Instant.EPOCH, Map.of());
    }
}
