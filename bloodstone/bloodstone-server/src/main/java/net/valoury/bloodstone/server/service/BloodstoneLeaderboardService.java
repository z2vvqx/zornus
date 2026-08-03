package net.valoury.bloodstone.server.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.valoury.bloodstone.server.BloodstoneServerConstants;
import net.valoury.bloodstone.server.BloodstoneText;
import net.valoury.bloodstone.server.model.GuildLeaderboardEntry;
import net.valoury.bloodstone.server.model.LeaderboardBoard;
import net.valoury.bloodstone.server.model.LeaderboardMetric;
import net.valoury.bloodstone.server.model.LeaderboardSnapshot;
import net.valoury.bloodstone.server.model.PlayerLeaderboardEntry;
import net.valoury.bloodstone.server.storage.BloodstoneLeaderboardStorage;
import net.valoury.guilds.api.GuildMembershipService;
import net.valoury.guilds.api.GuildProfile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class BloodstoneLeaderboardService {

    private final BloodstoneLeaderboardStorage storage;
    private final GuildMembershipService guildMembershipService;
    private final BloodstonePlayerNameService playerNameService;
    private final AtomicReference<LeaderboardSnapshot> snapshot =
            new AtomicReference<>(LeaderboardSnapshot.empty());

    public BloodstoneLeaderboardService(
            BloodstoneLeaderboardStorage storage,
            GuildMembershipService guildMembershipService,
            BloodstonePlayerNameService playerNameService
    ) {
        this.storage = storage;
        this.guildMembershipService = guildMembershipService;
        this.playerNameService = playerNameService;
    }

    public CompletableFuture<LeaderboardSnapshot> refresh() {
        CompletableFuture<List<PlayerLeaderboardEntry>> playerKills =
                storage.fetchPlayerLeaderboard(LeaderboardMetric.KILLS);
        CompletableFuture<List<PlayerLeaderboardEntry>> playerCurrent =
                storage.fetchPlayerLeaderboard(LeaderboardMetric.CURRENT_RAMPAGE);
        CompletableFuture<List<PlayerLeaderboardEntry>> playerBest =
                storage.fetchPlayerLeaderboard(LeaderboardMetric.BEST_RAMPAGE);
        CompletableFuture<List<GuildLeaderboardEntry>> guildKills =
                storage.fetchGuildLeaderboard(LeaderboardMetric.KILLS);
        CompletableFuture<List<GuildLeaderboardEntry>> guildCurrent =
                storage.fetchGuildLeaderboard(LeaderboardMetric.CURRENT_RAMPAGE);
        CompletableFuture<List<GuildLeaderboardEntry>> guildBest =
                storage.fetchGuildLeaderboard(LeaderboardMetric.BEST_RAMPAGE);

        return CompletableFuture.allOf(
                        playerKills,
                        playerCurrent,
                        playerBest,
                        guildKills,
                        guildCurrent,
                        guildBest
                )
                .thenCompose(ignored -> buildSnapshot(
                        playerKills.join(),
                        playerCurrent.join(),
                        playerBest.join(),
                        guildKills.join(),
                        guildCurrent.join(),
                        guildBest.join()
                ))
                .thenApply(completeSnapshot -> {
                    snapshot.set(completeSnapshot);
                    return completeSnapshot;
                });
    }

    public LeaderboardSnapshot snapshot() {
        return snapshot.get();
    }

    public String entry(LeaderboardBoard board, int oneBasedPosition) {
        if (oneBasedPosition < 1
                || oneBasedPosition > LeaderboardSnapshot.MAXIMUM_ENTRIES) {
            return emptyEntry(board);
        }
        List<String> entries = snapshot.get().entries().get(board);
        return oneBasedPosition <= entries.size()
                ? entries.get(oneBasedPosition - 1)
                : emptyEntry(board);
    }

    private CompletableFuture<LeaderboardSnapshot> buildSnapshot(
            List<PlayerLeaderboardEntry> playerKills,
            List<PlayerLeaderboardEntry> playerCurrent,
            List<PlayerLeaderboardEntry> playerBest,
            List<GuildLeaderboardEntry> guildKills,
            List<GuildLeaderboardEntry> guildCurrent,
            List<GuildLeaderboardEntry> guildBest
    ) {
        Map<UUID, CompletableFuture<Component>>
                playerNames = resolvePlayerNames(
                        playerKills,
                        playerCurrent,
                        playerBest
                );
        CompletableFuture<List<String>> formattedPlayerKills = formatPlayers(
                LeaderboardBoard.PLAYER_KILLS,
                playerKills,
                playerNames
        );
        CompletableFuture<List<String>> formattedPlayerCurrent = formatPlayers(
                LeaderboardBoard.PLAYER_CURRENT_RAMPAGE,
                playerCurrent,
                playerNames
        );
        CompletableFuture<List<String>> formattedPlayerBest = formatPlayers(
                LeaderboardBoard.PLAYER_BEST_RAMPAGE,
                playerBest,
                playerNames
        );
        CompletableFuture<List<String>> formattedGuildKills = formatGuilds(
                LeaderboardBoard.GUILD_KILLS,
                guildKills
        );
        CompletableFuture<List<String>> formattedGuildCurrent = formatGuilds(
                LeaderboardBoard.GUILD_CURRENT_RAMPAGE,
                guildCurrent
        );
        CompletableFuture<List<String>> formattedGuildBest = formatGuilds(
                LeaderboardBoard.GUILD_BEST_RAMPAGE,
                guildBest
        );

        return CompletableFuture.allOf(
                        formattedPlayerKills,
                        formattedPlayerCurrent,
                        formattedPlayerBest,
                        formattedGuildKills,
                        formattedGuildCurrent,
                        formattedGuildBest
                )
                .thenApply(ignored -> {
                    EnumMap<LeaderboardBoard, List<String>> entries =
                            new EnumMap<>(LeaderboardBoard.class);
                    entries.put(LeaderboardBoard.PLAYER_KILLS, formattedPlayerKills.join());
                    entries.put(LeaderboardBoard.PLAYER_CURRENT_RAMPAGE, formattedPlayerCurrent.join());
                    entries.put(LeaderboardBoard.PLAYER_BEST_RAMPAGE, formattedPlayerBest.join());
                    entries.put(LeaderboardBoard.GUILD_KILLS, formattedGuildKills.join());
                    entries.put(LeaderboardBoard.GUILD_CURRENT_RAMPAGE, formattedGuildCurrent.join());
                    entries.put(LeaderboardBoard.GUILD_BEST_RAMPAGE, formattedGuildBest.join());
                    return new LeaderboardSnapshot(Instant.now(), entries);
                });
    }

    private CompletableFuture<List<String>> formatPlayers(
            LeaderboardBoard board,
            List<PlayerLeaderboardEntry> entries,
            Map<UUID, CompletableFuture<Component>> playerNames
    ) {
        List<CompletableFuture<Optional<GuildProfile>>> guildLookups = entries.stream()
                .map(entry -> guildMembershipService.findGuildByPlayer(entry.playerId()))
                .toList();
        List<CompletableFuture<?>> pendingLookups =
                new ArrayList<>(entries.size() * 2);
        pendingLookups.addAll(guildLookups);
        for (PlayerLeaderboardEntry entry : entries) {
            pendingLookups.add(playerNames.get(entry.playerId()));
        }
        return CompletableFuture.allOf(
                        pendingLookups.toArray(CompletableFuture[]::new)
                )
                .thenApply(ignored -> {
                    List<String> formatted = new ArrayList<>(entries.size());
                    for (int index = 0; index < entries.size(); index++) {
                        PlayerLeaderboardEntry entry = entries.get(index);
                        Component playerName =
                                playerNames.get(entry.playerId()).join();
                        Optional<GuildProfile> guild = guildLookups.get(index).join();
                        Component guildDisplay = guild
                                .map(BloodstoneGuildText::tagDisplay)
                                .orElse(Component.empty());
                        formatted.add(BloodstoneText.legacy(
                                BloodstoneServerConstants
                                        .PLAYER_LEADERBOARD_ENTRY_FORMAT,
                                Placeholder.component("playername", playerName),
                                Placeholder.component("guild", guildDisplay),
                                Placeholder.component("icon", leaderboardIcon(board)),
                                Placeholder.unparsed(
                                        "value",
                                        Long.toString(entry.value())
                                )
                        ));
                    }
                    return List.copyOf(formatted);
                });
    }

    private Map<UUID, CompletableFuture<Component>> resolvePlayerNames(
            List<PlayerLeaderboardEntry> playerKills,
            List<PlayerLeaderboardEntry> playerCurrent,
            List<PlayerLeaderboardEntry> playerBest
    ) {
        Map<UUID, CompletableFuture<Component>> playerNames =
                new HashMap<>();
        for (List<PlayerLeaderboardEntry> entries : List.of(
                playerKills,
                playerCurrent,
                playerBest
        )) {
            for (PlayerLeaderboardEntry entry : entries) {
                playerNames.computeIfAbsent(
                        entry.playerId(),
                        ignored -> playerNameService.resolveStoredPlayerName(
                                entry.playerId(),
                                entry.username()
                        )
                );
            }
        }
        return Map.copyOf(playerNames);
    }

    private CompletableFuture<List<String>> formatGuilds(
            LeaderboardBoard board,
            List<GuildLeaderboardEntry> entries
    ) {
        List<CompletableFuture<Optional<GuildProfile>>> guildLookups = entries.stream()
                .map(entry -> guildMembershipService.findGuild(entry.guildId()))
                .toList();
        return CompletableFuture.allOf(guildLookups.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    List<String> formatted = new ArrayList<>(entries.size());
                    for (int index = 0; index < entries.size(); index++) {
                        GuildLeaderboardEntry entry = entries.get(index);
                        Optional<GuildProfile> guild = guildLookups.get(index).join();
                        if (guild.isEmpty()) {
                            continue;
                        }
                        GuildProfile profile = guild.get();
                        formatted.add(BloodstoneText.legacy(
                                BloodstoneServerConstants
                                        .GUILD_LEADERBOARD_ENTRY_FORMAT,
                                Placeholder.component(
                                        "guild",
                                        BloodstoneGuildText.nameAndTag(profile)
                                ),
                                Placeholder.component("icon", leaderboardIcon(board)),
                                Placeholder.unparsed(
                                        "value",
                                        Long.toString(entry.value())
                                )
                        ));
                    }
                    return List.copyOf(formatted);
                });
    }

    private String emptyEntry(LeaderboardBoard board) {
        return BloodstoneText.legacy(
                BloodstoneServerConstants.EMPTY_LEADERBOARD_ENTRY,
                Placeholder.component("icon", leaderboardIcon(board))
        );
    }

    private static Component leaderboardIcon(LeaderboardBoard board) {
        return switch (board) {
            case PLAYER_KILLS, GUILD_KILLS ->
                    Component.text("⚔", NamedTextColor.GREEN);
            case PLAYER_CURRENT_RAMPAGE, PLAYER_BEST_RAMPAGE,
                 GUILD_CURRENT_RAMPAGE, GUILD_BEST_RAMPAGE ->
                    Component.text("ᐃ", NamedTextColor.AQUA);
        };
    }
}
