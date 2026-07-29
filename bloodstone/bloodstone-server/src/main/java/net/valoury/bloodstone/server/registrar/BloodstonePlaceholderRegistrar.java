package net.valoury.bloodstone.server.registrar;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.valoury.bloodstone.server.model.LeaderboardBoard;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.service.BloodstoneLeaderboardService;
import net.valoury.bloodstone.server.service.BloodstoneGuildProfileCache;
import net.valoury.bloodstone.server.service.BloodstonePlayerService;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class BloodstonePlaceholderRegistrar {

    static final String GUILD_IDENTIFIER = "guild";
    static final String PLAYER_KILLS_IDENTIFIER = "top.player.kills";
    static final String PLAYER_RAMPAGE_IDENTIFIER = "top.player.rampage";
    static final String PLAYER_BEST_RAMPAGE_IDENTIFIER = "top.player.best.rampage";
    static final String GUILD_KILLS_IDENTIFIER = "top.guild.kills";
    static final String GUILD_RAMPAGE_IDENTIFIER = "top.guild.rampage";
    static final String GUILD_BEST_RAMPAGE_IDENTIFIER = "top.guild.best.rampage";
    static final String PLAYER_STATISTICS_IDENTIFIER = "player.stats";
    static final String CANONICAL_IDENTIFIER = "bloodstone";

    private final List<PlaceholderExpansion> expansions = new ArrayList<>();
    private final BloodstoneGuildProfileCache guildProfileCache;

    public BloodstonePlaceholderRegistrar(
            BloodstoneLeaderboardService leaderboardService,
            BloodstonePlayerService playerService,
            BloodstoneGuildProfileCache guildProfileCache
    ) {
        this.guildProfileCache = guildProfileCache;
        expansions.add(new BoardExpansion(PLAYER_KILLS_IDENTIFIER,
                LeaderboardBoard.PLAYER_KILLS, leaderboardService));
        expansions.add(new BoardExpansion(PLAYER_RAMPAGE_IDENTIFIER,
                LeaderboardBoard.PLAYER_CURRENT_RAMPAGE, leaderboardService));
        expansions.add(new BoardExpansion(PLAYER_BEST_RAMPAGE_IDENTIFIER,
                LeaderboardBoard.PLAYER_BEST_RAMPAGE, leaderboardService));
        expansions.add(new BoardExpansion(GUILD_KILLS_IDENTIFIER,
                LeaderboardBoard.GUILD_KILLS, leaderboardService));
        expansions.add(new BoardExpansion(GUILD_RAMPAGE_IDENTIFIER,
                LeaderboardBoard.GUILD_CURRENT_RAMPAGE, leaderboardService));
        expansions.add(new BoardExpansion(GUILD_BEST_RAMPAGE_IDENTIFIER,
                LeaderboardBoard.GUILD_BEST_RAMPAGE, leaderboardService));
        expansions.add(new PlayerStatsExpansion(playerService));
        expansions.add(new GuildExpansion(guildProfileCache));
        expansions.add(new CanonicalExpansion(
                leaderboardService,
                playerService,
                guildProfileCache
        ));
    }

    public void register() {
        for (PlaceholderExpansion expansion : expansions) {
            if (!expansion.register()) {
                throw new IllegalStateException(
                        "Failed to register PlaceholderAPI expansion "
                                + expansion.getIdentifier()
                );
            }
        }
    }

    public void unregister() {
        for (PlaceholderExpansion expansion : expansions) {
            expansion.unregister();
        }
    }

    private abstract static class BloodstoneExpansion extends PlaceholderExpansion {

        @Override
        public String getAuthor() {
            return "valoury";
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public boolean persist() {
            return true;
        }
    }

    private static final class BoardExpansion extends BloodstoneExpansion {

        private final String identifier;
        private final LeaderboardBoard board;
        private final BloodstoneLeaderboardService leaderboardService;

        private BoardExpansion(
                String identifier,
                LeaderboardBoard board,
                BloodstoneLeaderboardService leaderboardService
        ) {
            this.identifier = identifier;
            this.board = board;
            this.leaderboardService = leaderboardService;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String onRequest(@Nullable OfflinePlayer player, String parameters) {
            return boardEntry(leaderboardService, board, parameters);
        }
    }

    private static final class PlayerStatsExpansion extends BloodstoneExpansion {

        private final BloodstonePlayerService playerService;

        private PlayerStatsExpansion(BloodstonePlayerService playerService) {
            this.playerService = playerService;
        }

        @Override
        public String getIdentifier() {
            return PLAYER_STATISTICS_IDENTIFIER;
        }

        @Override
        public @Nullable String onRequest(@Nullable OfflinePlayer player, String parameters) {
            return playerStatistic(playerService, player, parameters);
        }
    }

    private static final class GuildExpansion extends BloodstoneExpansion {

        private final BloodstoneGuildProfileCache guildProfileCache;

        private GuildExpansion(BloodstoneGuildProfileCache guildProfileCache) {
            this.guildProfileCache = guildProfileCache;
        }

        @Override
        public String getIdentifier() {
            return GUILD_IDENTIFIER;
        }

        @Override
        public @Nullable String onRequest(@Nullable OfflinePlayer player, String parameters) {
            if (!"tag".equalsIgnoreCase(parameters)) {
                return null;
            }
            return player == null
                    ? ""
                    : guildProfileCache.legacyTag(player.getUniqueId());
        }
    }

    private static final class CanonicalExpansion extends BloodstoneExpansion {

        private final BloodstoneLeaderboardService leaderboardService;
        private final BloodstonePlayerService playerService;
        private final BloodstoneGuildProfileCache guildProfileCache;

        private CanonicalExpansion(
                BloodstoneLeaderboardService leaderboardService,
                BloodstonePlayerService playerService,
                BloodstoneGuildProfileCache guildProfileCache
        ) {
            this.leaderboardService = leaderboardService;
            this.playerService = playerService;
            this.guildProfileCache = guildProfileCache;
        }

        @Override
        public String getIdentifier() {
            return CANONICAL_IDENTIFIER;
        }

        @Override
        public @Nullable String onRequest(@Nullable OfflinePlayer player, String parameters) {
            String normalized = parameters.toLowerCase(Locale.ROOT);
            if ("guild_tag".equals(normalized)) {
                return player == null
                        ? ""
                        : guildProfileCache.legacyTag(player.getUniqueId());
            }
            if (normalized.startsWith("player_")) {
                return playerStatistic(
                        playerService,
                        player,
                        normalized.substring("player_".length())
                );
            }
            return canonicalBoard(normalized)
                    .map(boardRequest -> boardEntry(
                            leaderboardService,
                            boardRequest.board(),
                            boardRequest.position()
                    ))
                    .orElse(null);
        }

        private Optional<BoardRequest> canonicalBoard(String parameters) {
            for (Map.Entry<String, LeaderboardBoard> entry : Map.of(
                    "top_player_kills_", LeaderboardBoard.PLAYER_KILLS,
                    "top_player_current_rampage_", LeaderboardBoard.PLAYER_CURRENT_RAMPAGE,
                    "top_player_best_rampage_", LeaderboardBoard.PLAYER_BEST_RAMPAGE,
                    "top_guild_kills_", LeaderboardBoard.GUILD_KILLS,
                    "top_guild_current_rampage_", LeaderboardBoard.GUILD_CURRENT_RAMPAGE,
                    "top_guild_best_rampage_", LeaderboardBoard.GUILD_BEST_RAMPAGE
            ).entrySet()) {
                if (parameters.startsWith(entry.getKey())) {
                    return Optional.of(new BoardRequest(
                            entry.getValue(),
                            parameters.substring(entry.getKey().length())
                    ));
                }
            }
            return Optional.empty();
        }
    }

    private static @Nullable String playerStatistic(
            BloodstonePlayerService playerService,
            @Nullable OfflinePlayer player,
            String parameters
    ) {
        if (player == null) {
            return "0";
        }
        PlayerProfile profile = playerService.profile(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return "0";
        }
        return switch (parameters.toLowerCase(Locale.ROOT)) {
            case "kills" -> Integer.toString(profile.kills());
            case "deaths" -> Integer.toString(profile.deaths());
            case "ratio" -> String.format(Locale.US, "%.2f", profile.ratio());
            case "rampage", "current_rampage" -> Integer.toString(profile.currentRampage());
            case "best_rampage" -> Integer.toString(profile.bestRampage());
            case "assists" -> Integer.toString(profile.assists());
            case "carries" -> Integer.toString(profile.carries());
            case "dominations" -> Integer.toString(profile.dominations());
            case "revenges" -> Integer.toString(profile.revenges());
            default -> null;
        };
    }

    private static String boardEntry(
            BloodstoneLeaderboardService leaderboardService,
            LeaderboardBoard board,
            String position
    ) {
        try {
            return leaderboardService.entry(board, Integer.parseInt(position));
        } catch (NumberFormatException exception) {
            return leaderboardService.entry(board, 0);
        }
    }

    private record BoardRequest(LeaderboardBoard board, String position) {
    }
}
