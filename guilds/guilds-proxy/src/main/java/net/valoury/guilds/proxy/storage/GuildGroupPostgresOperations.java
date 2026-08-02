package net.valoury.guilds.proxy.storage;

import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.GuildGroupSettings;
import net.valoury.guilds.proxy.model.GuildRank;
import net.valoury.shared.model.GroupJoinPolicy;
import org.jspecify.annotations.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

final class GuildGroupPostgresOperations {

    private GuildGroupPostgresOperations() {
    }

    static @NonNull Optional<GuildGroupSettings> fetchSettings(
            @NonNull DataSource dataSource,
            @NonNull UUID guildId
    ) {
        String sql = "SELECT join_policy FROM guild_group_settings WHERE guild_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GuildGroupSettings(
                        guildId,
                        GroupJoinPolicy.fromStoredValue(resultSet.getString("join_policy"))
                ));
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to fetch guild group settings", exception);
        }
    }

    static @NonNull UpdateGuildJoinPolicyOutcome updateJoinPolicy(
            @NonNull DataSource dataSource,
            @NonNull UUID guildId,
            @NonNull UUID requesterId,
            @NonNull GroupJoinPolicy joinPolicy
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!lockGuild(connection, guildId)) {
                    connection.rollback();
                    return new UpdateGuildJoinPolicyOutcome.GuildNotFound();
                }

                Optional<GuildRank> requesterRank = fetchRequesterRank(
                        connection,
                        guildId,
                        requesterId
                );
                if (requesterRank.orElse(GuildRank.OUTCAST) != GuildRank.LEADER) {
                    connection.rollback();
                    return new UpdateGuildJoinPolicyOutcome.InsufficientRank();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE guild_group_settings
                        SET join_policy = ?
                        WHERE guild_id = ?
                        """)) {
                    statement.setString(1, joinPolicy.storedValue());
                    statement.setObject(2, guildId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Guild is missing its group settings row: " + guildId);
                    }
                }

                connection.commit();
                return new UpdateGuildJoinPolicyOutcome.Updated();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to update guild join policy", exception);
        }
    }

    static @NonNull JoinPublicGuildOutcome joinPublicGuild(
            @NonNull DataSource dataSource,
            @NonNull UUID guildId,
            @NonNull UUID playerId
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                acquireGuildJoinLock(connection, guildId);

                Optional<GroupJoinPolicy> joinPolicy = fetchJoinPolicy(connection, guildId);
                if (joinPolicy.isEmpty()) {
                    connection.rollback();
                    return new JoinPublicGuildOutcome.GuildNotFound();
                }
                if (isGuildMember(connection, playerId)) {
                    connection.rollback();
                    return new JoinPublicGuildOutcome.AlreadyInGuild();
                }
                if (joinPolicy.get() != GroupJoinPolicy.PUBLIC) {
                    connection.rollback();
                    return new JoinPublicGuildOutcome.GuildPrivate();
                }
                if (memberCount(connection, guildId) >= GuildProxyConstants.MAX_GUILD_SIZE) {
                    connection.rollback();
                    return new JoinPublicGuildOutcome.GuildFull();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO guild_members (guild_id, player_id, guild_rank)
                        VALUES (?, ?, ?)
                        """)) {
                    statement.setObject(1, guildId);
                    statement.setObject(2, playerId);
                    statement.setString(3, GuildRank.OUTCAST.displayName());
                    statement.executeUpdate();
                }

                connection.commit();
                return new JoinPublicGuildOutcome.Joined();
            } catch (SQLException exception) {
                connection.rollback();
                if ("23505".equals(exception.getSQLState())) {
                    return new JoinPublicGuildOutcome.AlreadyInGuild();
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to join public guild", exception);
        }
    }

    private static boolean lockGuild(
            @NonNull Connection connection,
            @NonNull UUID guildId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM guilds WHERE guild_id = ? FOR UPDATE")) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static @NonNull Optional<GuildRank> fetchRequesterRank(
            @NonNull Connection connection,
            @NonNull UUID guildId,
            @NonNull UUID requesterId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT guild_rank
                FROM guild_members
                WHERE guild_id = ? AND player_id = ?
                """)) {
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

    private static void acquireGuildJoinLock(
            @NonNull Connection connection,
            @NonNull UUID guildId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 1))")) {
            statement.setString(1, guildId.toString());
            statement.executeQuery();
        }
    }

    private static @NonNull Optional<GroupJoinPolicy> fetchJoinPolicy(
            @NonNull Connection connection,
            @NonNull UUID guildId
    ) throws SQLException {
        String sql = """
                SELECT settings.join_policy
                FROM guilds guild
                JOIN guild_group_settings settings USING (guild_id)
                WHERE guild_id = ?
                FOR UPDATE OF guild
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(GroupJoinPolicy.fromStoredValue(
                                resultSet.getString("join_policy")))
                        : Optional.empty();
            }
        }
    }

    private static boolean isGuildMember(
            @NonNull Connection connection,
            @NonNull UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM guild_members WHERE player_id = ?")) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static int memberCount(
            @NonNull Connection connection,
            @NonNull UUID guildId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM guild_members WHERE guild_id = ?")) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
