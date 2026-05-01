package com.zornus.friends.proxy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.model.*;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FriendPostgresStorage implements FriendStorage, AutoCloseable {

    private final HikariDataSource dataSource;
    private final ExecutorService databaseExecutor;

    public FriendPostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(FriendProxyConstants.DATABASE_CONNECTION_POOL_SIZE);
        config.setDriverClassName("org.postgresql.Driver");
        this.dataSource = new HikariDataSource(config);
        this.databaseExecutor = Executors.newFixedThreadPool(FriendProxyConstants.DATABASE_EXECUTOR_POOL_SIZE);
        try {
            initializeSchema();
        } catch (RuntimeException exception) {
            databaseExecutor.shutdown();
            try {
                databaseExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            databaseExecutor.shutdownNow();
            dataSource.close();
            throw exception;
        }
    }

    @Override
    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(FriendProxyConstants.DATABASE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        player_id UUID PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        last_joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        last_seen_at TIMESTAMPTZ
                    )
                    """);
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_players_username ON players(username)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS relations (
                        player1 UUID NOT NULL REFERENCES players(player_id),
                        player2 UUID NOT NULL REFERENCES players(player_id),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (player1, player2),
                        CHECK (player1 < player2)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_relations_player1 ON relations(player1)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_relations_player2 ON relations(player2)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS requests (
                        sender UUID NOT NULL REFERENCES players(player_id),
                        receiver UUID NOT NULL REFERENCES players(player_id),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (sender, receiver)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_requests_receiver ON requests(receiver)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_requests_sender ON requests(sender)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_requests_receiver_created ON requests(receiver, created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_requests_sender_created ON requests(sender, created_at DESC)");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        player_id UUID PRIMARY KEY,
                        presence_state VARCHAR(16) NOT NULL DEFAULT 'online',
                        allow_messages BOOLEAN NOT NULL DEFAULT TRUE,
                        allow_jump BOOLEAN NOT NULL DEFAULT TRUE,
                        show_last_seen BOOLEAN NOT NULL DEFAULT TRUE,
                        show_location BOOLEAN NOT NULL DEFAULT FALSE,
                        accept_requests BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS last_message (
                        player_id UUID PRIMARY KEY,
                        sender_id UUID NOT NULL,
                        timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS request_cooldowns (
                        sender_id UUID NOT NULL,
                        receiver_id UUID NOT NULL,
                        timestamp TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (sender_id, receiver_id)
                    )
                    """);

        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize database schema", exception);
        }
    }

    @Contract("_, _ -> new")
    private @NonNull CanonicalUuidPair canonicalizePair(@NonNull UUID player1, @NonNull UUID player2) {
        if (player1.toString().compareTo(player2.toString()) > 0) {
            return new CanonicalUuidPair(player2, player1);
        }
        return new CanonicalUuidPair(player1, player2);
    }

    private <T> T executeQuery(String sql, SQLParameterSetter parameterSetter, ResultSetMapper<T> resultMapper, String operationName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameterSetter != null) {
                parameterSetter.setParameters(statement);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultMapper.map(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to " + operationName, exception);
        }
    }

    private int executeUpdate(String sql, SQLParameterSetter parameterSetter, String operationName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameterSetter != null) {
                parameterSetter.setParameters(statement);
            }
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to " + operationName, exception);
        }
    }

    @Override
    public CompletableFuture<Boolean> addFriendRequest(FriendRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO requests (sender, receiver, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, request.senderUuid());
                statement.setObject(2, request.receiverUuid());
                statement.setTimestamp(3, Timestamp.from(request.timestamp()));
            }, "add friend request");
            return rows > 0;
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> removeFriendRequest(UUID sender, UUID receiver) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM requests WHERE sender = ? AND receiver = ?";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, sender);
                statement.setObject(2, receiver);
            }, "remove friend request");
            return rows > 0;
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<FriendRequest>> fetchFriendRequest(UUID sender, UUID receiver) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    SELECT r.sender, r.receiver, r.created_at,
                           p1.username AS sender_username, p2.username AS receiver_username
                    FROM requests r
                    JOIN players p1 ON r.sender = p1.player_id
                    JOIN players p2 ON r.receiver = p2.player_id
                    WHERE r.sender = ? AND r.receiver = ?
                    """;
            return executeQuery(sql, statement -> {
                statement.setObject(1, sender);
                statement.setObject(2, receiver);
            }, resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToFriendRequest(resultSet));
                }
                return Optional.empty();
            }, "get friend request");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<List<FriendRequest>> fetchIncomingFriendRequests(UUID receiver) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    SELECT r.sender, r.receiver, r.created_at,
                           p1.username AS sender_username, p2.username AS receiver_username
                    FROM requests r
                    JOIN players p1 ON r.sender = p1.player_id
                    JOIN players p2 ON r.receiver = p2.player_id
                    WHERE r.receiver = ? ORDER BY r.created_at DESC
                    """;
            return executeQuery(sql, statement -> statement.setObject(1, receiver), resultSet -> {
                List<FriendRequest> requests = new ArrayList<>();
                while (resultSet.next()) {
                    requests.add(mapResultSetToFriendRequest(resultSet));
                }
                return requests;
            }, "get incoming requests");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<List<FriendRequest>> fetchOutgoingFriendRequests(UUID sender) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    SELECT r.sender, r.receiver, r.created_at,
                           p1.username AS sender_username, p2.username AS receiver_username
                    FROM requests r
                    JOIN players p1 ON r.sender = p1.player_id
                    JOIN players p2 ON r.receiver = p2.player_id
                    WHERE r.sender = ? ORDER BY r.created_at DESC
                    """;
            return executeQuery(sql, statement -> statement.setObject(1, sender), resultSet -> {
                List<FriendRequest> requests = new ArrayList<>();
                while (resultSet.next()) {
                    requests.add(mapResultSetToFriendRequest(resultSet));
                }
                return requests;
            }, "get outgoing requests");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Integer> countIncomingFriendRequests(UUID receiver) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM requests WHERE receiver = ?";
            return executeQuery(sql, statement -> statement.setObject(1, receiver), resultSet -> {
                resultSet.next();
                return resultSet.getInt(1);
            }, "count incoming requests");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Integer> countOutgoingFriendRequests(UUID sender) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM requests WHERE sender = ?";
            return executeQuery(sql, statement -> statement.setObject(1, sender), resultSet -> {
                resultSet.next();
                return resultSet.getInt(1);
            }, "count outgoing requests");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> hasIncomingFriendRequest(UUID receiver, UUID sender) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM requests WHERE receiver = ? AND sender = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, receiver);
                statement.setObject(2, sender);
            }, ResultSet::next, "check incoming request");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> hasOutgoingFriendRequest(UUID sender, UUID receiver) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM requests WHERE sender = ? AND receiver = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, sender);
                statement.setObject(2, receiver);
            }, ResultSet::next, "check outgoing request");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> addFriendRelation(UUID player1, UUID player2) {
        return CompletableFuture.supplyAsync(() -> {
            CanonicalUuidPair pair = canonicalizePair(player1, player2);
            String sql = "INSERT INTO relations (player1, player2, created_at) VALUES (?, ?, NOW()) ON CONFLICT DO NOTHING";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, pair.firstPlayer());
                statement.setObject(2, pair.secondPlayer());
            }, "add relation");
            return rows > 0;
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> removeFriendRelation(UUID player1, UUID player2) {
        return CompletableFuture.supplyAsync(() -> {
            CanonicalUuidPair pair = canonicalizePair(player1, player2);
            String sql = "DELETE FROM relations WHERE player1 = ? AND player2 = ?";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, pair.firstPlayer());
                statement.setObject(2, pair.secondPlayer());
            }, "remove relation");
            return rows > 0;
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Boolean> hasFriendRelation(UUID player1, UUID player2) {
        return CompletableFuture.supplyAsync(() -> {
            CanonicalUuidPair pair = canonicalizePair(player1, player2);
            String sql = "SELECT 1 FROM relations WHERE player1 = ? AND player2 = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, pair.firstPlayer());
                statement.setObject(2, pair.secondPlayer());
            }, ResultSet::next, "check friend relation");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<List<FriendRelation>> fetchFriendRelations(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    SELECT r.player1, r.player2, r.created_at,
                           p1.username AS player1_username, p2.username AS player2_username
                    FROM relations r
                    JOIN players p1 ON r.player1 = p1.player_id
                    JOIN players p2 ON r.player2 = p2.player_id
                    WHERE r.player1 = ? OR r.player2 = ? ORDER BY r.created_at DESC
                    """;
            return executeQuery(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setObject(2, playerId);
            }, resultSet -> {
                List<FriendRelation> relations = new ArrayList<>();
                while (resultSet.next()) {
                    relations.add(mapResultSetToFriendRelation(resultSet));
                }
                return relations;
            }, "get relations");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Integer> fetchFriendRelationCount(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM relations WHERE player1 = ? OR player2 = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setObject(2, playerId);
            }, resultSet -> {
                resultSet.next();
                return resultSet.getInt(1);
            }, "get relation count");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<FriendSettings>> fetchSettings(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_id, presence_state, allow_messages, allow_jump, show_last_seen, show_location, accept_requests FROM settings WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToFriendSettings(resultSet));
                }
                return Optional.empty();
            }, "get settings");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> saveSettings(UUID playerId, FriendSettings newSettings) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO settings (player_id, presence_state, allow_messages, allow_jump, show_last_seen, show_location, accept_requests)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET
                        presence_state = EXCLUDED.presence_state,
                        allow_messages = EXCLUDED.allow_messages,
                        allow_jump = EXCLUDED.allow_jump,
                        show_last_seen = EXCLUDED.show_last_seen,
                        show_location = EXCLUDED.show_location,
                        accept_requests = EXCLUDED.accept_requests
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, newSettings.presenceState().name().toLowerCase());
                statement.setBoolean(3, newSettings.allowMessages());
                statement.setBoolean(4, newSettings.allowJump());
                statement.setBoolean(5, newSettings.showLastSeen());
                statement.setBoolean(6, newSettings.showLocation());
                statement.setBoolean(7, newSettings.allowRequests());
            }, "update settings");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateAllowMessages(UUID playerId, boolean value) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO settings (player_id, allow_messages) VALUES (?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET allow_messages = EXCLUDED.allow_messages
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update allow_messages");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateAllowJump(UUID playerId, boolean value) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO settings (player_id, allow_jump) VALUES (?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET allow_jump = EXCLUDED.allow_jump
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update allow_jump");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateShowLastSeen(UUID playerId, boolean value) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO settings (player_id, show_last_seen) VALUES (?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET show_last_seen = EXCLUDED.show_last_seen
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update show_last_seen");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateShowLocation(UUID playerId, boolean value) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO settings (player_id, show_location) VALUES (?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET show_location = EXCLUDED.show_location
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update show_location");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updateAllowRequests(UUID playerId, boolean value) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO settings (player_id, accept_requests) VALUES (?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET accept_requests = EXCLUDED.accept_requests
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update accept_requests");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> updatePresenceState(UUID playerId, PresenceState value) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO settings (player_id, presence_state) VALUES (?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET presence_state = EXCLUDED.presence_state
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, value.name().toLowerCase());
            }, "update presence_state");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> upsertPlayer(UUID playerId, String username) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO players (player_id, username, last_joined_at)
                    VALUES (?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET
                        username = EXCLUDED.username,
                        last_joined_at = EXCLUDED.last_joined_at
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, username);
            }, "upsert player");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_id, username FROM players WHERE username = ?";
            return executeQuery(sql, statement -> statement.setString(1, username), resultSet -> {
                if (resultSet.next()) {
                    UUID playerId = (UUID) resultSet.getObject("player_id");
                    String playerUsername = resultSet.getString("username");
                    return Optional.of(new PlayerRecord(playerId, playerUsername));
                }
                return Optional.empty();
            }, "get player by username");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUuid(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT player_id, username FROM players WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    UUID uuid = (UUID) resultSet.getObject("player_id");
                    String username = resultSet.getString("username");
                    return Optional.of(new PlayerRecord(uuid, username));
                }
                return Optional.empty();
            }, "get player by uuid");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> saveLastSeen(UUID playerId, Instant timestamp) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE players SET last_seen_at = ? WHERE player_id = ?";
            executeUpdate(sql, statement -> {
                statement.setTimestamp(1, Timestamp.from(timestamp));
                statement.setObject(2, playerId);
            }, "record last seen");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<Instant>> fetchLastSeen(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT last_seen_at FROM players WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    Timestamp timestamp = resultSet.getTimestamp("last_seen_at");
                    return timestamp != null ? Optional.of(timestamp.toInstant()) : Optional.empty();
                }
                return Optional.empty();
            }, "get last seen");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> saveLastMessageSender(UUID playerId, UUID senderId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO last_message (player_id, sender_id, timestamp) VALUES (?, ?, NOW()) ON CONFLICT (player_id) DO UPDATE SET sender_id = EXCLUDED.sender_id, timestamp = EXCLUDED.timestamp";
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setObject(2, senderId);
            }, "record last message sender");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<UUID>> fetchLastMessageSender(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT sender_id FROM last_message WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of((UUID) resultSet.getObject("sender_id"));
                }
                return Optional.empty();
            }, "get last message sender");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Optional<Instant>> fetchFriendRequestTimestamp(UUID senderId, UUID receiverId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT timestamp FROM request_cooldowns WHERE sender_id = ? AND receiver_id = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, senderId);
                statement.setObject(2, receiverId);
            }, resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getTimestamp("timestamp").toInstant());
                }
                return Optional.empty();
            }, "get last request timestamp");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> saveFriendRequestTimestamp(UUID senderId, UUID receiverId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO request_cooldowns (sender_id, receiver_id, timestamp) VALUES (?, ?, NOW()) ON CONFLICT (sender_id, receiver_id) DO UPDATE SET timestamp = EXCLUDED.timestamp";
            executeUpdate(sql, statement -> {
                statement.setObject(1, senderId);
                statement.setObject(2, receiverId);
            }, "record request timestamp");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredFriendRequests(Instant now, Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM requests WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired requests");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredFriendRequestCooldowns(Instant now, Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM request_cooldowns WHERE timestamp < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired cooldowns");
        }, databaseExecutor);
    }

    @Override
    public CompletableFuture<Void> cleanupExpiredLastMessageSenders(Instant now, Duration expiry) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM last_message WHERE timestamp < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired last message senders");
        }, databaseExecutor);
    }

    @Contract("_ -> new")
    private @NonNull FriendRequest mapResultSetToFriendRequest(@NonNull ResultSet resultSet) throws SQLException {
        return new FriendRequest(
                (UUID) resultSet.getObject("sender"),
                resultSet.getString("sender_username"),
                (UUID) resultSet.getObject("receiver"),
                resultSet.getString("receiver_username"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private @NonNull FriendRelation mapResultSetToFriendRelation(@NonNull ResultSet resultSet) throws SQLException {
        UUID player1 = (UUID) resultSet.getObject("player1");
        UUID player2 = (UUID) resultSet.getObject("player2");
        String player1Username = resultSet.getString("player1_username");
        String player2Username = resultSet.getString("player2_username");
        Instant createdAt = resultSet.getTimestamp("created_at").toInstant();
        return new FriendRelation(player1, player1Username, player2, player2Username, createdAt);
    }

    private @NonNull FriendSettings mapResultSetToFriendSettings(@NonNull ResultSet resultSet) throws SQLException {
        String presenceStateString = resultSet.getString("presence_state");
        PresenceState presenceState = presenceStateString != null
                ? PresenceState.valueOf(presenceStateString.toUpperCase())
                : PresenceState.ONLINE;
        return new FriendSettings(
                (UUID) resultSet.getObject("player_id"),
                presenceState,
                resultSet.getBoolean("allow_messages"),
                resultSet.getBoolean("allow_jump"),
                resultSet.getBoolean("show_last_seen"),
                resultSet.getBoolean("show_location"),
                resultSet.getBoolean("accept_requests")
        );
    }

    @FunctionalInterface
    private interface SQLParameterSetter {
        void setParameters(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    private record CanonicalUuidPair(UUID firstPlayer, UUID secondPlayer) {
    }
}
