package net.valoury.bloodstone.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LeaderboardSnapshotTest {

    @Test
    void snapshotCopiesEveryBoardBeforeAtomicPublication() {
        ArrayList<String> mutableEntries = new ArrayList<>(List.of("one"));
        EnumMap<LeaderboardBoard, List<String>> mutableBoards =
                new EnumMap<>(LeaderboardBoard.class);
        mutableBoards.put(LeaderboardBoard.PLAYER_KILLS, mutableEntries);

        LeaderboardSnapshot snapshot = new LeaderboardSnapshot(Instant.EPOCH, mutableBoards);
        mutableEntries.add("two");
        mutableBoards.clear();

        assertEquals(List.of("one"),
                snapshot.entries().get(LeaderboardBoard.PLAYER_KILLS));
        assertEquals(LeaderboardBoard.values().length, snapshot.entries().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.entries().put(LeaderboardBoard.GUILD_KILLS, List.of()));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.entries().get(LeaderboardBoard.PLAYER_KILLS).add("two"));
    }
}
