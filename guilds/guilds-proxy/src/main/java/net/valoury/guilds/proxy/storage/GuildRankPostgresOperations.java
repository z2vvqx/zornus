package net.valoury.guilds.proxy.storage;

import net.valoury.guilds.proxy.model.GuildRank;
import net.valoury.guilds.proxy.model.GuildRankChangeDirection;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class GuildRankPostgresOperations {

    private GuildRankPostgresOperations() {
    }

    static @NonNull GuildRankChangeOutcome changeMemberRank(
            @NonNull DataSource dataSource,
            @NonNull UUID guildId,
            @NonNull UUID actorId,
            @NonNull UUID targetId,
            @NonNull GuildRankChangeDirection direction
    ) {
        if (actorId.equals(targetId)) {
            return new GuildRankChangeOutcome.CannotChangeSelf();
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<UUID, GuildRank> memberRanks =
                        lockMemberRanks(connection, guildId, actorId, targetId);
                if (memberRanks.size() < 2) {
                    GuildRankChangeOutcome missingMemberOutcome =
                            classifyMissingMember(
                                    connection,
                                    guildId,
                                    actorId,
                                    memberRanks
                            );
                    connection.rollback();
                    return missingMemberOutcome;
                }

                GuildRank actorRank = memberRanks.get(actorId);
                GuildRank targetRank = memberRanks.get(targetId);
                Optional<GuildRankChangeOutcome> deniedOutcome =
                        validateRankChange(actorRank, targetRank, direction);
                if (deniedOutcome.isPresent()) {
                    connection.rollback();
                    return deniedOutcome.get();
                }

                GuildRank newRank = direction == GuildRankChangeDirection.PROMOTION
                        ? targetRank.nextHigher().orElseThrow()
                        : targetRank.nextLower().orElseThrow();
                if (!updateMemberRank(
                        connection,
                        guildId,
                        targetId,
                        targetRank,
                        newRank
                )) {
                    connection.rollback();
                    return new GuildRankChangeOutcome.MemberNotFound();
                }

                connection.commit();
                return new GuildRankChangeOutcome.Changed(targetRank, newRank);
            } catch (SQLException exception) {
                connection.rollback();
                throw new RuntimeException("Failed to change guild member rank", exception);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to change guild member rank", exception);
        }
    }

    private static @NonNull Map<UUID, GuildRank> lockMemberRanks(
            @NonNull Connection connection,
            @NonNull UUID guildId,
            @NonNull UUID actorId,
            @NonNull UUID targetId
    ) throws SQLException {
        String sql = """
                SELECT player_id, guild_rank
                FROM guild_members
                WHERE guild_id = ? AND player_id IN (?, ?)
                ORDER BY player_id
                FOR UPDATE
                """;
        Map<UUID, GuildRank> memberRanks = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guildId);
            statement.setObject(2, actorId);
            statement.setObject(3, targetId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    memberRanks.put(
                            resultSet.getObject("player_id", UUID.class),
                            GuildRank.fromStoredName(resultSet.getString("guild_rank"))
                    );
                }
            }
        }
        return memberRanks;
    }

    private static @NonNull GuildRankChangeOutcome classifyMissingMember(
            @NonNull Connection connection,
            @NonNull UUID guildId,
            @NonNull UUID actorId,
            @NonNull Map<UUID, GuildRank> memberRanks
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM guilds WHERE guild_id = ?")) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new GuildRankChangeOutcome.GuildNotFound();
                }
            }
        }
        return memberRanks.containsKey(actorId)
                ? new GuildRankChangeOutcome.MemberNotFound()
                : new GuildRankChangeOutcome.ActorNotMember();
    }

    private static @NonNull Optional<GuildRankChangeOutcome> validateRankChange(
            @NonNull GuildRank actorRank,
            @NonNull GuildRank targetRank,
            @NonNull GuildRankChangeDirection direction
    ) {
        if (!actorRank.canChangeRanks()) {
            return Optional.of(new GuildRankChangeOutcome.InsufficientRank());
        }
        if (!actorRank.isHigherThan(targetRank)) {
            return Optional.of(new GuildRankChangeOutcome.CannotManageRank());
        }

        if (direction == GuildRankChangeDirection.PROMOTION) {
            Optional<GuildRank> promotedRank = targetRank.nextHigher();
            if (promotedRank.isEmpty()) {
                return Optional.of(new GuildRankChangeOutcome.AlreadyHighestRank());
            }
            if (promotedRank.get() == actorRank) {
                return Optional.of(
                        new GuildRankChangeOutcome.PromotionWouldMatchActorRank());
            }
            return actorRank.canPromote(targetRank)
                    ? Optional.empty()
                    : Optional.of(new GuildRankChangeOutcome.CannotManageRank());
        }

        if (targetRank.nextLower().isEmpty()) {
            return Optional.of(new GuildRankChangeOutcome.AlreadyLowestRank());
        }
        return actorRank.canDemote(targetRank)
                ? Optional.empty()
                : Optional.of(new GuildRankChangeOutcome.CannotManageRank());
    }

    private static boolean updateMemberRank(
            @NonNull Connection connection,
            @NonNull UUID guildId,
            @NonNull UUID targetId,
            @NonNull GuildRank previousRank,
            @NonNull GuildRank newRank
    ) throws SQLException {
        String sql = """
                UPDATE guild_members
                SET guild_rank = ?
                WHERE guild_id = ? AND player_id = ? AND guild_rank = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newRank.displayName());
            statement.setObject(2, guildId);
            statement.setObject(3, targetId);
            statement.setString(4, previousRank.displayName());
            return statement.executeUpdate() == 1;
        }
    }

    static @NonNull UpdateGuildColorOutcome updateGuildColor(
            @NonNull DataSource dataSource,
            @NonNull UUID guildId,
            @NonNull UUID requesterId,
            @NonNull String guildColor
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<GuildRank> requesterRank =
                        lockMemberRank(connection, guildId, requesterId);
                if (requesterRank.isEmpty()) {
                    boolean guildExists = guildExists(connection, guildId);
                    connection.rollback();
                    return guildExists
                            ? new UpdateGuildColorOutcome.InsufficientRank()
                            : new UpdateGuildColorOutcome.GuildNotFound();
                }
                if (!requesterRank.get().canUpdateColor()) {
                    connection.rollback();
                    return new UpdateGuildColorOutcome.InsufficientRank();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE guilds SET guild_color = ? WHERE guild_id = ?")) {
                    statement.setString(1, guildColor);
                    statement.setObject(2, guildId);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return new UpdateGuildColorOutcome.GuildNotFound();
                    }
                }

                connection.commit();
                return new UpdateGuildColorOutcome.Updated();
            } catch (SQLException exception) {
                connection.rollback();
                throw new RuntimeException("Failed to update guild color", exception);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to update guild color", exception);
        }
    }

    private static @NonNull Optional<GuildRank> lockMemberRank(
            @NonNull Connection connection,
            @NonNull UUID guildId,
            @NonNull UUID requesterId
    ) throws SQLException {
        String sql = """
                SELECT guild_rank
                FROM guild_members
                WHERE guild_id = ? AND player_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guildId);
            statement.setObject(2, requesterId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(GuildRank.fromStoredName(
                                resultSet.getString("guild_rank")))
                        : Optional.empty();
            }
        }
    }

    private static boolean guildExists(
            @NonNull Connection connection,
            @NonNull UUID guildId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM guilds WHERE guild_id = ?")) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
