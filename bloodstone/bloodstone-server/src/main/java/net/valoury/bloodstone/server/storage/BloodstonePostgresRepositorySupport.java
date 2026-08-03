package net.valoury.bloodstone.server.storage;

import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

final class BloodstonePostgresRepositorySupport {

    private BloodstonePostgresRepositorySupport() {
    }

    static void validateOfferClaim(
            UUID playerId,
            String offerKey,
            Instant now,
            Duration cooldown
    ) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        validateKey(offerKey, "Offer key");
        Objects.requireNonNull(now, "Claim time cannot be null");
        requirePositive(cooldown, "Enchanter offer cooldown");
    }

    static void validateKey(String value, String description) {
        Objects.requireNonNull(value, description + " cannot be null");
        if (value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(
                    description + " must contain between 1 and 128 characters");
        }
    }

    static void requireNonNegative(long value, String description) {
        if (value < 0) {
            throw new IllegalArgumentException(description + " cannot be negative");
        }
    }

    static void requirePositive(Duration duration, String description) {
        Objects.requireNonNull(duration, description + " cannot be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(description + " must be positive");
        }
    }

    static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    static byte @Nullable [] copy(byte @Nullable [] value) {
        return value == null ? null : value.clone();
    }

    static @Nullable Instant getInstant(ResultSet resultSet, String column)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    static void setInstant(
            PreparedStatement statement,
            int index,
            @Nullable Instant instant
    ) throws SQLException {
        statement.setTimestamp(index, instant == null ? null : Timestamp.from(instant));
    }

    static @Nullable Long getNullableLong(ResultSet resultSet, String column)
            throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

}
