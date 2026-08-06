package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;
import net.valoury.bloodstone.server.model.StorageSession;
import net.valoury.bloodstone.server.model.StorageType;
import net.valoury.shared.database.DatabaseExecutor;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class BloodstonePostgresInventoryRepository {

    private final HikariDataSource dataSource;
    private final DatabaseExecutor databaseExecutor;
    private final Set<UUID> ownedSessions = ConcurrentHashMap.newKeySet();

    BloodstonePostgresInventoryRepository(
            HikariDataSource dataSource,
            DatabaseExecutor databaseExecutor
    ) {
        this.dataSource = dataSource;
        this.databaseExecutor = databaseExecutor;
    }

    CompletableFuture<ExtraStorageUnlockOutcome> unlockExtraStorage(
            UUID operationId,
            UUID playerId,
            Instant now
    ) {
        Objects.requireNonNull(operationId, "Operation ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(now, "Purchase time cannot be null");
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement existing =
                                 connection.prepareStatement("""
                            SELECT 1
                            FROM bloodstone_extra_storage_purchases
                            WHERE operation_id = ? AND player_id = ?
                            """)) {
                        existing.setObject(1, operationId);
                        existing.setObject(2, playerId);
                        try (ResultSet resultSet = existing.executeQuery()) {
                            if (resultSet.next()) {
                                connection.commit();
                                return new ExtraStorageUnlockOutcome.Unlocked();
                            }
                        }
                    }
                    boolean unlocked;
                    try (PreparedStatement playerLookup =
                                 connection.prepareStatement("""
                            SELECT extra_storage_unlocked
                            FROM bloodstone_players
                            WHERE player_id = ?
                            FOR UPDATE
                            """)) {
                        playerLookup.setObject(1, playerId);
                        try (ResultSet resultSet =
                                     playerLookup.executeQuery()) {
                            if (!resultSet.next()) {
                                connection.rollback();
                                return new ExtraStorageUnlockOutcome
                                        .PlayerNotFound();
                            }
                            unlocked = resultSet.getBoolean(
                                    "extra_storage_unlocked"
                            );
                        }
                    }
                    if (unlocked) {
                        connection.rollback();
                        return new ExtraStorageUnlockOutcome.AlreadyUnlocked();
                    }
                    try (PreparedStatement unlock =
                                 connection.prepareStatement("""
                            UPDATE bloodstone_players
                            SET extra_storage_unlocked = TRUE,
                                version = version + 1
                            WHERE player_id = ?
                            """);
                         PreparedStatement purchase =
                                 connection.prepareStatement("""
                            INSERT INTO bloodstone_extra_storage_purchases
                                (operation_id, player_id, purchased_at)
                            VALUES (?, ?, ?)
                            """)) {
                        unlock.setObject(1, playerId);
                        unlock.executeUpdate();
                        purchase.setObject(1, operationId);
                        purchase.setObject(2, playerId);
                        setInstant(purchase, 3, now);
                        purchase.executeUpdate();
                    }
                    connection.commit();
                    return new ExtraStorageUnlockOutcome.Unlocked();
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to unlock extra storage for " + playerId,
                        exception
                );
            }
        });
    }

    CompletableFuture<StorageOpenOutcome> open(
            UUID playerId,
            StorageType storageType,
            UUID sessionToken,
            Instant now,
            Duration leaseDuration
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(storageType, "Storage type cannot be null");
        Objects.requireNonNull(sessionToken, "Session token cannot be null");
        Objects.requireNonNull(now, "Open time cannot be null");
        requirePositive(leaseDuration, "Storage lease duration");
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Boolean extraUnlocked = findExtraStorageUnlock(
                            connection,
                            playerId
                    );
                    if (extraUnlocked == null) {
                        connection.rollback();
                        return new StorageOpenOutcome.PlayerNotFound();
                    }
                    if (storageType == StorageType.EXTRA && !extraUnlocked) {
                        connection.rollback();
                        return new StorageOpenOutcome.Locked();
                    }
                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                            INSERT INTO bloodstone_storage_contents (
                                player_id,
                                storage_type
                            )
                            VALUES (?, ?)
                            ON CONFLICT (player_id, storage_type) DO NOTHING
                            """)) {
                        statement.setObject(1, playerId);
                        statement.setString(
                                2,
                                storageType.name()
                        );
                        statement.executeUpdate();
                    }

                    Instant leaseExpiresAt = now.plus(leaseDuration);
                    StorageSession session = null;
                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                            UPDATE bloodstone_storage_contents
                            SET session_token = ?, lease_expires_at = ?
                            WHERE player_id = ? AND storage_type = ?
                              AND (
                                  session_token IS NULL
                                  OR lease_expires_at <= ?
                                  OR session_token = ?
                              )
                            RETURNING contents_payload, version
                            """)) {
                        statement.setObject(1, sessionToken);
                        setInstant(statement, 2, leaseExpiresAt);
                        statement.setObject(3, playerId);
                        statement.setString(
                                4,
                                storageType.name()
                        );
                        setInstant(statement, 5, now);
                        statement.setObject(6, sessionToken);
                        try (ResultSet resultSet =
                                     statement.executeQuery()) {
                            if (resultSet.next()) {
                                session = new StorageSession(
                                        playerId,
                                        storageType,
                                        sessionToken,
                                        resultSet.getBytes(
                                                "contents_payload"
                                        ),
                                        resultSet.getLong("version"),
                                        leaseExpiresAt
                                );
                            }
                        }
                    }
                    if (session != null) {
                        connection.commit();
                        ownedSessions.add(sessionToken);
                        return new StorageOpenOutcome.Opened(session);
                    }
                    Instant activeLease;
                    try (PreparedStatement statement =
                                 connection.prepareStatement("""
                            SELECT lease_expires_at
                            FROM bloodstone_storage_contents
                            WHERE player_id = ? AND storage_type = ?
                            """)) {
                        statement.setObject(1, playerId);
                        statement.setString(
                                2,
                                storageType.name()
                        );
                        try (ResultSet resultSet =
                                     statement.executeQuery()) {
                            if (!resultSet.next()) {
                                throw new SQLException(
                                        "Storage row disappeared during open"
                                );
                            }
                            activeLease = getInstant(
                                    resultSet,
                                    "lease_expires_at"
                            );
                        }
                    }
                    connection.commit();
                    return new StorageOpenOutcome.InUse(activeLease);
                } catch (SQLException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to open " + storageType
                                + " storage for " + playerId,
                        exception
                );
            }
        });
    }

    CompletableFuture<StorageWriteOutcome> checkpoint(
            StorageSession session,
            byte @Nullable [] contentsPayload,
            Instant now,
            Duration leaseDuration
    ) {
        Objects.requireNonNull(session, "Storage session cannot be null");
        Objects.requireNonNull(now, "Checkpoint time cannot be null");
        requirePositive(leaseDuration, "Storage lease duration");
        byte[] payload = copy(contentsPayload);
        return databaseExecutor.supply(() -> {
            Instant leaseExpiresAt = now.plus(leaseDuration);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement("""
                         UPDATE bloodstone_storage_contents
                         SET contents_payload = ?, version = version + 1,
                             lease_expires_at = ?, updated_at = NOW()
                         WHERE player_id = ? AND storage_type = ?
                           AND session_token = ? AND version = ?
                         RETURNING version
                         """)) {
                statement.setBytes(1, payload);
                setInstant(statement, 2, leaseExpiresAt);
                statement.setObject(3, session.playerId());
                statement.setString(
                        4,
                        session.storageType().name()
                );
                statement.setObject(5, session.sessionToken());
                statement.setLong(6, session.version());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return findCompletedCheckpoint(
                                connection,
                                session,
                                payload
                        );
                    }
                    return new StorageWriteOutcome.Saved(
                            new StorageSession(
                                    session.playerId(),
                                    session.storageType(),
                                    session.sessionToken(),
                                    payload,
                                    resultSet.getLong("version"),
                                    leaseExpiresAt
                            )
                    );
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to checkpoint storage session "
                                + session.sessionToken(),
                        exception
                );
            }
        });
    }

    CompletableFuture<StorageWriteOutcome> close(
            StorageSession session,
            byte @Nullable [] contentsPayload,
            Instant now
    ) {
        Objects.requireNonNull(session, "Storage session cannot be null");
        Objects.requireNonNull(now, "Close time cannot be null");
        byte[] payload = copy(contentsPayload);
        return databaseExecutor.supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement("""
                         UPDATE bloodstone_storage_contents
                         SET contents_payload = ?, version = version + 1,
                             session_token = NULL,
                             lease_expires_at = NULL,
                             updated_at = NOW()
                         WHERE player_id = ? AND storage_type = ?
                           AND session_token = ? AND version = ?
                         RETURNING version
                         """)) {
                statement.setBytes(1, payload);
                statement.setObject(2, session.playerId());
                statement.setString(
                        3,
                        session.storageType().name()
                );
                statement.setObject(4, session.sessionToken());
                statement.setLong(5, session.version());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return findCompletedClose(
                                connection,
                                session,
                                payload,
                                now
                        );
                    }
                    ownedSessions.remove(session.sessionToken());
                    return new StorageWriteOutcome.Saved(
                            new StorageSession(
                                    session.playerId(),
                                    session.storageType(),
                                    session.sessionToken(),
                                    payload,
                                    resultSet.getLong("version"),
                                    now
                            )
                    );
                }
            } catch (SQLException exception) {
                throw new RuntimeException(
                        "Failed to close storage session "
                                + session.sessionToken(),
                        exception
                );
            }
        });
    }

    void releaseOwnedSessions() {
        if (ownedSessions.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE bloodstone_storage_contents
                     SET session_token = NULL, lease_expires_at = NULL
                     WHERE session_token = ?
                     """)) {
            for (UUID sessionToken : Set.copyOf(ownedSessions)) {
                statement.setObject(1, sessionToken);
                statement.addBatch();
            }
            statement.executeBatch();
            ownedSessions.clear();
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Failed to release Bloodstone storage sessions",
                    exception
            );
        }
    }

    private StorageWriteOutcome findCompletedCheckpoint(
            Connection connection,
            StorageSession session,
            byte[] payload
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version, lease_expires_at
                FROM bloodstone_storage_contents
                WHERE player_id = ? AND storage_type = ?
                  AND session_token = ?
                  AND version = ?
                  AND contents_payload IS NOT DISTINCT FROM ?
                """)) {
            statement.setObject(1, session.playerId());
            statement.setString(
                    2,
                    session.storageType().name()
            );
            statement.setObject(3, session.sessionToken());
            statement.setLong(4, session.version() + 1);
            statement.setBytes(5, payload);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new StorageWriteOutcome.SessionConflict();
                }
                return new StorageWriteOutcome.Saved(new StorageSession(
                        session.playerId(),
                        session.storageType(),
                        session.sessionToken(),
                        payload,
                        resultSet.getLong("version"),
                        getInstant(resultSet, "lease_expires_at")
                ));
            }
        }
    }

    private StorageWriteOutcome findCompletedClose(
            Connection connection,
            StorageSession session,
            byte[] payload,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version
                FROM bloodstone_storage_contents
                WHERE player_id = ? AND storage_type = ?
                  AND session_token IS NULL
                  AND version = ?
                  AND contents_payload IS NOT DISTINCT FROM ?
                """)) {
            statement.setObject(1, session.playerId());
            statement.setString(
                    2,
                    session.storageType().name()
            );
            statement.setLong(3, session.version() + 1);
            statement.setBytes(4, payload);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new StorageWriteOutcome.SessionConflict();
                }
                ownedSessions.remove(session.sessionToken());
                return new StorageWriteOutcome.Saved(new StorageSession(
                        session.playerId(),
                        session.storageType(),
                        session.sessionToken(),
                        payload,
                        resultSet.getLong("version"),
                        now
                ));
            }
        }
    }

    private static Boolean findExtraStorageUnlock(
            Connection connection,
            UUID playerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT extra_storage_unlocked
                FROM bloodstone_players
                WHERE player_id = ?
                """)) {
            statement.setObject(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? resultSet.getBoolean("extra_storage_unlocked")
                        : null;
            }
        }
    }

    private static void requirePositive(
            Duration duration,
            String description
    ) {
        Objects.requireNonNull(duration, description + " cannot be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    description + " must be positive"
            );
        }
    }

    private static void rollback(
            Connection connection,
            SQLException original
    ) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static byte @Nullable [] copy(byte @Nullable [] value) {
        return value == null ? null : value.clone();
    }

    private static @Nullable Instant getInstant(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void setInstant(
            PreparedStatement statement,
            int index,
            @Nullable Instant instant
    ) throws SQLException {
        statement.setTimestamp(
                index,
                instant == null ? null : Timestamp.from(instant)
        );
    }
}
