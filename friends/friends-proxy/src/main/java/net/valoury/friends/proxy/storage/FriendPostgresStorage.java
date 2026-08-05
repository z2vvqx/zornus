package net.valoury.friends.proxy.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.valoury.friends.proxy.FriendProxyConstants;
import net.valoury.friends.proxy.model.FriendRelation;
import net.valoury.friends.proxy.model.FriendRequest;
import net.valoury.friends.proxy.model.FriendSettings;
import net.valoury.friends.proxy.model.PresenceState;
import net.valoury.shared.database.DatabaseDefaults;
import net.valoury.shared.database.DatabaseExecutor;
import net.valoury.shared.database.PostgresSchemaVerifier;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.shared.utilities.CooldownKey;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class FriendPostgresStorage implements FriendStorage, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendPostgresStorage.class);

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;

    public FriendPostgresStorage(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(FriendProxyConstants.DATABASE_CONNECTION_POOL_SIZE);
        config.setDriverClassName("org.postgresql.Driver");
        config.setConnectionTimeout(DatabaseDefaults.CONNECTION_ACQUISITION_TIMEOUT_MILLISECONDS);
        config.setValidationTimeout(DatabaseDefaults.CONNECTION_VALIDATION_TIMEOUT_MILLISECONDS);
        config.addDataSourceProperty(
                "connectTimeout", DatabaseDefaults.CONNECTION_ESTABLISHMENT_TIMEOUT_SECONDS);
        config.addDataSourceProperty("socketTimeout", DatabaseDefaults.SOCKET_READ_TIMEOUT_SECONDS);
        config.addDataSourceProperty(
                "cancelSignalTimeout", DatabaseDefaults.CANCEL_SIGNAL_TIMEOUT_SECONDS);
        config.addDataSourceProperty("options", DatabaseDefaults.POSTGRESQL_SESSION_OPTIONS);
        this.dataSource = new HikariDataSource(config);
        this.databaseExecutor = new DatabaseExecutor(
                "friends-database-",
                FriendProxyConstants.DATABASE_EXECUTOR_POOL_SIZE
        );
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
            connection.setAutoCommit(false);
            try {
                if (PostgresSchemaVerifier.relationExists(connection, "players")) {
                    validateSchema(connection);
                    connection.commit();
                    return;
                }
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        player_id UUID PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        last_joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        last_seen_at TIMESTAMPTZ
                    )
                    """);
                // Usernames are not a stable identity: Mojang allows renames and recycles freed
                // names, so a UNIQUE constraint here causes a later player's join to collide with
                // a stale row still holding their new name under a different player_id. player_id
                // is the only identity we can rely on being unique; username is just a
                // last-known display name, resolved by recency in fetchPlayerByUsername.
                statement.execute("CREATE INDEX IF NOT EXISTS idx_players_username_lower ON players (LOWER(username))");

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
                statement.execute("CREATE INDEX IF NOT EXISTS idx_requests_created ON requests(created_at)");

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        player_id UUID PRIMARY KEY REFERENCES players(player_id),
                        presence_state VARCHAR(16) NOT NULL DEFAULT 'online',
                        allow_messages BOOLEAN NOT NULL DEFAULT TRUE,
                        allow_jump BOOLEAN NOT NULL DEFAULT TRUE,
                        show_last_seen BOOLEAN NOT NULL DEFAULT TRUE,
                        show_location BOOLEAN NOT NULL DEFAULT TRUE,
                        accept_requests BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """);

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS last_message (
                        player_id UUID PRIMARY KEY REFERENCES players(player_id),
                        sender_id UUID NOT NULL REFERENCES players(player_id),
                        timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_last_message_timestamp ON last_message(timestamp)");

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS request_cooldowns (
                        sender_id UUID NOT NULL REFERENCES players(player_id),
                        receiver_id UUID NOT NULL REFERENCES players(player_id),
                        timestamp TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (sender_id, receiver_id)
                    )
                    """);
                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_request_cooldowns_timestamp
                    ON request_cooldowns(timestamp)
                    """);

                validateSchema(connection);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize database schema", exception);
        }
    }

    private static void validateSchema(Connection connection) throws SQLException {
        PostgresSchemaVerifier.requireRelations(
                connection,
                "players",
                "idx_players_username_lower",
                "relations",
                "idx_relations_player1",
                "idx_relations_player2",
                "requests",
                "idx_requests_receiver",
                "idx_requests_sender",
                "idx_requests_receiver_created",
                "idx_requests_sender_created",
                "idx_requests_created",
                "settings",
                "last_message",
                "idx_last_message_timestamp",
                "request_cooldowns",
                "idx_request_cooldowns_timestamp"
        );
        PostgresSchemaVerifier.requireColumns(
                connection, "players", "player_id", "username", "last_joined_at", "last_seen_at");
        PostgresSchemaVerifier.requireColumns(
                connection, "relations", "player1", "player2", "created_at");
        PostgresSchemaVerifier.requireColumns(
                connection, "requests", "sender", "receiver", "created_at");
        PostgresSchemaVerifier.requireColumns(
                connection,
                "settings",
                "player_id",
                "presence_state",
                "allow_messages",
                "allow_jump",
                "show_last_seen",
                "show_location",
                "accept_requests"
        );
        PostgresSchemaVerifier.requireColumns(
                connection, "last_message", "player_id", "sender_id", "timestamp");
        PostgresSchemaVerifier.requireColumns(
                connection, "request_cooldowns", "sender_id", "receiver_id", "timestamp");
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

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Boolean> removeFriendRequest(UUID sender, UUID receiver) {
        return databaseExecutor.supply(() -> {
            String sql = "DELETE FROM requests WHERE sender = ? AND receiver = ?";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, sender);
                statement.setObject(2, receiver);
            }, "remove friend request");
            return rows > 0;
        });
    }

    @Contract("_ -> new")
    @Override
    public @NonNull CompletableFuture<Integer> countIncomingFriendRequests(UUID receiverId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT COUNT(*) FROM requests WHERE receiver = ?";
            return executeQuery(sql, statement -> statement.setObject(1, receiverId), resultSet -> {
                resultSet.next();
                return resultSet.getInt(1);
            }, "count incoming friend requests");
        });
    }

    @Contract("_ -> new")
    @Override
    public @NonNull CompletableFuture<List<FriendRequest>> fetchIncomingFriendRequests(UUID receiver) {
        return databaseExecutor.supply(() -> {
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
        });
    }

    @Contract("_ -> new")
    @Override
    public @NonNull CompletableFuture<List<FriendRequest>> fetchOutgoingFriendRequests(UUID sender) {
        return databaseExecutor.supply(() -> {
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
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Boolean> removeFriendRelation(UUID player1, UUID player2) {
        return databaseExecutor.supply(() -> {
            CooldownKey.CanonicalKey pair = CooldownKey.canonicalize(player1, player2);
            String sql = "DELETE FROM relations WHERE player1 = ? AND player2 = ?";
            int rows = executeUpdate(sql, statement -> {
                statement.setObject(1, pair.smaller());
                statement.setObject(2, pair.larger());
            }, "remove relation");
            return rows > 0;
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Boolean> hasFriendRelation(UUID player1, UUID player2) {
        return databaseExecutor.supply(() -> {
            CooldownKey.CanonicalKey pair = CooldownKey.canonicalize(player1, player2);
            String sql = "SELECT 1 FROM relations WHERE player1 = ? AND player2 = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, pair.smaller());
                statement.setObject(2, pair.larger());
            }, ResultSet::next, "check friend relation");
        });
    }

    @Contract("_ -> new")
    @Override
    public @NonNull CompletableFuture<List<FriendRelation>> fetchFriendRelations(UUID playerId) {
        return databaseExecutor.supply(() -> {
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
        });
    }

    @Contract("_ -> new")
    @Override
    public @NonNull CompletableFuture<Optional<FriendSettings>> fetchSettings(UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT player_id, presence_state, allow_messages, allow_jump, show_last_seen, show_location, accept_requests FROM settings WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToFriendSettings(resultSet));
                }
                return Optional.empty();
            }, "get settings");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> updateAllowMessages(UUID playerId, boolean value) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO settings (player_id, allow_messages, show_location) VALUES (?, ?, TRUE)
                    ON CONFLICT (player_id) DO UPDATE SET allow_messages = EXCLUDED.allow_messages
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update allow_messages");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> updateAllowJump(UUID playerId, boolean value) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO settings (player_id, allow_jump, show_location) VALUES (?, ?, TRUE)
                    ON CONFLICT (player_id) DO UPDATE SET allow_jump = EXCLUDED.allow_jump
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update allow_jump");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> updateShowLastSeen(UUID playerId, boolean value) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO settings (player_id, show_last_seen, show_location) VALUES (?, ?, TRUE)
                    ON CONFLICT (player_id) DO UPDATE SET show_last_seen = EXCLUDED.show_last_seen
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update show_last_seen");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> updateShowLocation(UUID playerId, boolean value) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO settings (player_id, show_location) VALUES (?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET show_location = EXCLUDED.show_location
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update show_location");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> updateAllowRequests(UUID playerId, boolean value) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO settings (player_id, accept_requests, show_location) VALUES (?, ?, TRUE)
                    ON CONFLICT (player_id) DO UPDATE SET accept_requests = EXCLUDED.accept_requests
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setBoolean(2, value);
            }, "update accept_requests");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> updatePresenceState(UUID playerId, PresenceState value) {
        return databaseExecutor.run(() -> {
            String sql = """
                    INSERT INTO settings (player_id, presence_state, show_location) VALUES (?, ?, TRUE)
                    ON CONFLICT (player_id) DO UPDATE SET presence_state = EXCLUDED.presence_state
                    """;
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setString(2, value.name().toLowerCase());
            }, "update presence_state");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> upsertPlayer(UUID playerId, String username) {
        return databaseExecutor.run(() -> {
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
        });
    }

    @Contract("_ -> new")
    @Override
    public @NonNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUsername(String username) {
        return databaseExecutor.supply(() -> {
            // Case-insensitive match; if a name was recycled between accounts, the most
            // recently-joined owner is the correct match. The returned "username" column is
            // the stored value, not the input, so callers get the correct current casing.
            String sql = """
                    SELECT player_id, username FROM players
                    WHERE LOWER(username) = LOWER(?)
                    ORDER BY last_joined_at DESC
                    LIMIT 1
                    """;
            return executeQuery(sql, statement -> statement.setString(1, username), resultSet -> {
                if (resultSet.next()) {
                    UUID playerId = (UUID) resultSet.getObject("player_id");
                    String playerUsername = resultSet.getString("username");
                    return Optional.of(new PlayerRecord(playerId, playerUsername));
                }
                return Optional.empty();
            }, "get player by username");
        });
    }

    @Contract("_ -> new")
    @Override
    public @NonNull CompletableFuture<Optional<PlayerRecord>> fetchPlayerByUuid(UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT player_id, username FROM players WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    UUID uuid = (UUID) resultSet.getObject("player_id");
                    String username = resultSet.getString("username");
                    return Optional.of(new PlayerRecord(uuid, username));
                }
                return Optional.empty();
            }, "get player by uuid");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Boolean> saveLastSeenIfPresenceOnline(UUID playerId, Instant timestamp) {
        return databaseExecutor.supply(() -> {
            String sql = """
                    UPDATE players
                    SET last_seen_at = ?
                    WHERE player_id = ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM settings
                          WHERE settings.player_id = players.player_id
                            AND settings.presence_state = 'offline'
                      )
                    """;
            int updatedPlayers = executeUpdate(sql, statement -> {
                statement.setTimestamp(1, Timestamp.from(timestamp));
                statement.setObject(2, playerId);
            }, "record visible last seen");
            return updatedPlayers > 0;
        });
    }

    @Contract("_ -> new")
    @Override
    public @NonNull CompletableFuture<Optional<Instant>> fetchLastSeen(UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT last_seen_at FROM players WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    Timestamp timestamp = resultSet.getTimestamp("last_seen_at");
                    return timestamp != null ? Optional.of(timestamp.toInstant()) : Optional.empty();
                }
                return Optional.empty();
            }, "get last seen");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> saveLastMessageSender(UUID playerId, UUID senderId) {
        return databaseExecutor.run(() -> {
            String sql = "INSERT INTO last_message (player_id, sender_id, timestamp) VALUES (?, ?, NOW()) ON CONFLICT (player_id) DO UPDATE SET sender_id = EXCLUDED.sender_id, timestamp = EXCLUDED.timestamp";
            executeUpdate(sql, statement -> {
                statement.setObject(1, playerId);
                statement.setObject(2, senderId);
            }, "record last message sender");
        });
    }

    @Contract("_ -> new")
    @Override
    public @NonNull CompletableFuture<Optional<UUID>> fetchLastMessageSender(UUID playerId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT sender_id FROM last_message WHERE player_id = ?";
            return executeQuery(sql, statement -> statement.setObject(1, playerId), resultSet -> {
                if (resultSet.next()) {
                    return Optional.of((UUID) resultSet.getObject("sender_id"));
                }
                return Optional.empty();
            }, "get last message sender");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Optional<Instant>> fetchFriendRequestCooldown(UUID senderId, UUID receiverId) {
        return databaseExecutor.supply(() -> {
            String sql = "SELECT timestamp FROM request_cooldowns WHERE sender_id = ? AND receiver_id = ?";
            return executeQuery(sql, statement -> {
                statement.setObject(1, senderId);
                statement.setObject(2, receiverId);
            }, resultSet -> {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getTimestamp("timestamp").toInstant());
                }
                return Optional.empty();
            }, "get friend request cooldown");
        });
    }

    // Compound operations

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<SendRequestOutcome> trySendFriendRequest(UUID senderId, UUID receiverId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try {
                    // Serialize request and friend limit checks per player
                    acquirePerPlayerLocks(connection, senderId, receiverId);

                    // 1. Check if already friends
                    CooldownKey.CanonicalKey pair = CooldownKey.canonicalize(senderId, receiverId);
                    String checkFriendsSql = "SELECT 1 FROM relations WHERE player1 = ? AND player2 = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkFriendsSql)) {
                        statement.setObject(1, pair.smaller());
                        statement.setObject(2, pair.larger());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new SendRequestOutcome.AlreadyFriends();
                            }
                        }
                    }

                    // 2. Check if there's an incoming request from the receiver
                    String checkIncomingSql = "SELECT 1 FROM requests WHERE sender = ? AND receiver = ?";
                    boolean hasIncomingRequest;
                    try (PreparedStatement statement = connection.prepareStatement(checkIncomingSql)) {
                        statement.setObject(1, receiverId);
                        statement.setObject(2, senderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            hasIncomingRequest = resultSet.next();
                        }
                    }

                    if (hasIncomingRequest) {
                        connection.rollback();
                        return new SendRequestOutcome.IncomingRequestExists();
                    }

                    // 3. Check if request already exists (outgoing)
                    String checkOutgoingSql = "SELECT 1 FROM requests WHERE sender = ? AND receiver = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkOutgoingSql)) {
                        statement.setObject(1, senderId);
                        statement.setObject(2, receiverId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new SendRequestOutcome.RequestAlreadySent();
                            }
                        }
                    }

                    // 4. Check if the receiver accepts requests
                    String checkSettingsSql = "SELECT accept_requests FROM settings WHERE player_id = ?";
                    boolean acceptsRequests = true;
                    try (PreparedStatement statement = connection.prepareStatement(checkSettingsSql)) {
                        statement.setObject(1, receiverId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                acceptsRequests = resultSet.getBoolean("accept_requests");
                            }
                        }
                    }
                    if (!acceptsRequests) {
                        connection.rollback();
                        return new SendRequestOutcome.PlayerNotAcceptingRequests();
                    }

                    // 5. Check request cooldown
                    String checkCooldownSql = "SELECT timestamp FROM request_cooldowns WHERE sender_id = ? AND receiver_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkCooldownSql)) {
                        statement.setObject(1, senderId);
                        statement.setObject(2, receiverId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Timestamp lastTimestamp = resultSet.getTimestamp("timestamp");
                                Instant nextAllowed = lastTimestamp.toInstant().plus(FriendProxyConstants.FRIEND_REQUEST_COOLDOWN);
                                if (Instant.now().isBefore(nextAllowed)) {
                                    connection.rollback();
                                    return new SendRequestOutcome.RequestCooldownActive();
                                }
                            }
                        }
                    }

                    // 6. Check request limits (total sent + received per player)
                    String countSenderTotalSql = """
                            SELECT (SELECT COUNT(*) FROM requests WHERE sender = ?) +
                                   (SELECT COUNT(*) FROM requests WHERE receiver = ?)
                            """;
                    int senderTotal;
                    try (PreparedStatement statement = connection.prepareStatement(countSenderTotalSql)) {
                        statement.setObject(1, senderId);
                        statement.setObject(2, senderId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            senderTotal = resultSet.getInt(1);
                        }
                    }
                    if (senderTotal >= FriendProxyConstants.MAX_FRIEND_REQUESTS) {
                        connection.rollback();
                        return new SendRequestOutcome.SenderRequestLimitReached();
                    }

                    String countReceiverTotalSql = """
                            SELECT (SELECT COUNT(*) FROM requests WHERE sender = ?) +
                                   (SELECT COUNT(*) FROM requests WHERE receiver = ?)
                            """;
                    int receiverTotal;
                    try (PreparedStatement statement = connection.prepareStatement(countReceiverTotalSql)) {
                        statement.setObject(1, receiverId);
                        statement.setObject(2, receiverId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            resultSet.next();
                            receiverTotal = resultSet.getInt(1);
                        }
                    }
                    if (receiverTotal >= FriendProxyConstants.MAX_FRIEND_REQUESTS) {
                        connection.rollback();
                        return new SendRequestOutcome.ReceiverRequestLimitReached();
                    }

                    // 7. Check friend limits
                    int senderFriendCount = countFriendsInTransaction(connection, senderId);
                    if (senderFriendCount >= FriendProxyConstants.MAX_FRIENDS) {
                        connection.rollback();
                        return new SendRequestOutcome.SenderFriendsLimitReached();
                    }

                    int receiverFriendCount = countFriendsInTransaction(connection, receiverId);
                    if (receiverFriendCount >= FriendProxyConstants.MAX_FRIENDS) {
                        connection.rollback();
                        return new SendRequestOutcome.ReceiverFriendsLimitReached();
                    }

                    // 8. Insert the friend request
                    String insertRequestSql = "INSERT INTO requests (sender, receiver, created_at) VALUES (?, ?, NOW())";
                    try (PreparedStatement statement = connection.prepareStatement(insertRequestSql)) {
                        statement.setObject(1, senderId);
                        statement.setObject(2, receiverId);
                        statement.executeUpdate();
                    }

                    // 9. Record/refresh cooldown
                    String upsertCooldownSql = """
                            INSERT INTO request_cooldowns (sender_id, receiver_id, timestamp) VALUES (?, ?, NOW())
                            ON CONFLICT (sender_id, receiver_id) DO UPDATE SET timestamp = EXCLUDED.timestamp
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(upsertCooldownSql)) {
                        statement.setObject(1, senderId);
                        statement.setObject(2, receiverId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new SendRequestOutcome.Sent();
                } catch (SQLException exception) {
                    connection.rollback();
                    // Check for unique violation (already friends or request already exists)
                    if ("23505".equals(exception.getSQLState()) || "40001".equals(exception.getSQLState())) {
                        // Could be either already friends or request already sent
                        // Check which one by querying
                        try {
                            CooldownKey.CanonicalKey pair = CooldownKey.canonicalize(senderId, receiverId);
                            String checkSql = "SELECT 1 FROM relations WHERE player1 = ? AND player2 = ?";
                            try (PreparedStatement checkStatement = connection.prepareStatement(checkSql)) {
                                checkStatement.setObject(1, pair.smaller());
                                checkStatement.setObject(2, pair.larger());
                                try (ResultSet checkResultSet = checkStatement.executeQuery()) {
                                    boolean alreadyFriends = checkResultSet.next();
                                    connection.rollback(); // close out the implicit read-only transaction opened above
                                    if (alreadyFriends) {
                                        return new SendRequestOutcome.AlreadyFriends();
                                    }
                                }
                            }
                            return new SendRequestOutcome.RequestAlreadySent();
                        } catch (SQLException innerException) {
                            throw new RuntimeException("Failed to determine conflict type", innerException);
                        }
                    }
                    throw new RuntimeException("Failed to send friend request", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to send friend request", exception);
            }
        });
    }

    private int countFriendsInTransaction(Connection connection, UUID playerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM relations WHERE player1 = ? OR player2 = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, playerId);
            statement.setObject(2, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private void acquirePerPlayerLocks(@NonNull Connection connection, @NonNull UUID player1, UUID player2) throws SQLException {
        UUID smaller = player1.compareTo(player2) < 0 ? player1 : player2;
        UUID larger = smaller.equals(player1) ? player2 : player1;
        try (PreparedStatement lockStatement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0)), pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            lockStatement.setString(1, smaller.toString());
            lockStatement.setString(2, larger.toString());
            lockStatement.executeQuery();
        }
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<AcceptRequestOutcome> acceptFriendRequest(UUID accepterId, UUID requesterId) {
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                // Serialize friend limit checks per player
                acquirePerPlayerLocks(connection, accepterId, requesterId);

                // Serialize accepts per player to prevent concurrent accepts exceeding MAX_FRIENDS
                // Lock in canonical UUID order to prevent deadlocks
                UUID smaller = accepterId.compareTo(requesterId) < 0 ? accepterId : requesterId;
                UUID larger = smaller.equals(accepterId) ? requesterId : accepterId;
                try (PreparedStatement lockStatement = connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock(hashtextextended(?, 2)), pg_advisory_xact_lock(hashtextextended(?, 2))")) {
                    lockStatement.setString(1, smaller.toString());
                    lockStatement.setString(2, larger.toString());
                    lockStatement.executeQuery();
                }

                try {
                    // 1. Verify the incoming request exists
                    String checkRequestSql = "SELECT 1 FROM requests WHERE sender = ? AND receiver = ?";
                    boolean hasRequest;
                    try (PreparedStatement statement = connection.prepareStatement(checkRequestSql)) {
                        statement.setObject(1, requesterId);
                        statement.setObject(2, accepterId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            hasRequest = resultSet.next();
                        }
                    }
                    if (!hasRequest) {
                        connection.rollback();
                        return new AcceptRequestOutcome.NoRequestFound();
                    }

                    // 2. Check if already friends
                    CooldownKey.CanonicalKey pair = CooldownKey.canonicalize(accepterId, requesterId);
                    String checkFriendsSql = "SELECT 1 FROM relations WHERE player1 = ? AND player2 = ?";
                    try (PreparedStatement statement = connection.prepareStatement(checkFriendsSql)) {
                        statement.setObject(1, pair.smaller());
                        statement.setObject(2, pair.larger());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                connection.rollback();
                                return new AcceptRequestOutcome.AlreadyFriends();
                            }
                        }
                    }

                    // 3. Check friend limits
                    int accepterFriendCount = countFriendsInTransaction(connection, accepterId);
                    if (accepterFriendCount >= FriendProxyConstants.MAX_FRIENDS) {
                        connection.rollback();
                        return new AcceptRequestOutcome.AccepterFriendsLimitReached();
                    }

                    int requesterFriendCount = countFriendsInTransaction(connection, requesterId);
                    if (requesterFriendCount >= FriendProxyConstants.MAX_FRIENDS) {
                        connection.rollback();
                        return new AcceptRequestOutcome.RequesterFriendsLimitReached();
                    }

                    // 4. Insert friend relation
                    String insertRelationSql = "INSERT INTO relations (player1, player2, created_at) VALUES (?, ?, NOW())";
                    try (PreparedStatement statement = connection.prepareStatement(insertRelationSql)) {
                        statement.setObject(1, pair.smaller());
                        statement.setObject(2, pair.larger());
                        statement.executeUpdate();
                    }

                    // 5. Delete the friend request
                    String deleteRequestSql = "DELETE FROM requests WHERE sender = ? AND receiver = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteRequestSql)) {
                        statement.setObject(1, requesterId);
                        statement.setObject(2, accepterId);
                        statement.executeUpdate();
                    }

                    connection.commit();
                    return new AcceptRequestOutcome.Accepted();
                } catch (SQLException exception) {
                    connection.rollback();
                    // Check for unique violation (already friends)
                    if ("23505".equals(exception.getSQLState())) {
                        return new AcceptRequestOutcome.AlreadyFriends();
                    }
                    throw new RuntimeException("Failed to accept friend request", exception);
                }
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to accept friend request", exception);
            }
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> cleanupExpiredFriendRequests(Instant now, Duration expiry) {
        return databaseExecutor.run(() -> {
            String sql = "DELETE FROM requests WHERE created_at < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired requests");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> cleanupExpiredFriendRequestCooldowns(Instant now, Duration expiry) {
        return databaseExecutor.run(() -> {
            String sql = "DELETE FROM request_cooldowns WHERE timestamp < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired cooldowns");
        });
    }

    @Contract("_, _ -> new")
    @Override
    public @NonNull CompletableFuture<Void> cleanupExpiredLastMessageSenders(Instant now, Duration expiry) {
        return databaseExecutor.run(() -> {
            String sql = "DELETE FROM last_message WHERE timestamp < ?";
            executeUpdate(sql, statement -> statement.setTimestamp(1, Timestamp.from(now.minus(expiry))), "cleanup expired last message senders");
        });
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
        PresenceState presenceState = PresenceState.ONLINE;
        if (presenceStateString != null) {
            try {
                presenceState = PresenceState.valueOf(presenceStateString.toUpperCase());
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Unknown presence_state '{}' for player {}, defaulting to ONLINE",
                        presenceStateString, resultSet.getObject("player_id"));
            }
        }
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

}
