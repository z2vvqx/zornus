package net.valoury.bloodstone.server.storage;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

final class BloodstonePostgresSchema {

    private BloodstonePostgresSchema() {
    }

    static void initialize(HikariDataSource dataSource) {
        Objects.requireNonNull(dataSource, "Data source cannot be null");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement rootTableLookup = connection.prepareStatement(
                    "SELECT to_regclass('public.bloodstone_players') IS NOT NULL"
            ); ResultSet resultSet = rootTableLookup.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("PostgreSQL did not return a schema-existence result");
                }
                if (resultSet.getBoolean(1)) {
                    requireExistingTable(
                            connection,
                            "bloodstone_axe_fuser_operations"
                    );
                    connection.rollback();
                    return;
                }
            }

            try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_players (
                        player_id UUID PRIMARY KEY,
                        username VARCHAR(16) NOT NULL
                            CHECK (username ~ '^[A-Za-z0-9_]{1,16}$'),
                        kills INTEGER NOT NULL DEFAULT 0 CHECK (kills >= 0),
                        deaths INTEGER NOT NULL DEFAULT 0 CHECK (deaths >= 0),
                        assists INTEGER NOT NULL DEFAULT 0 CHECK (assists >= 0),
                        carries INTEGER NOT NULL DEFAULT 0 CHECK (carries >= 0),
                        dominations INTEGER NOT NULL DEFAULT 0 CHECK (dominations >= 0),
                        revenges INTEGER NOT NULL DEFAULT 0 CHECK (revenges >= 0),
                        current_rampage INTEGER NOT NULL DEFAULT 0 CHECK (current_rampage >= 0),
                        best_rampage INTEGER NOT NULL DEFAULT 0 CHECK (best_rampage >= current_rampage),
                        extra_storage_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
                        version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
                        last_joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_players_username_lower
                    ON bloodstone_players (LOWER(username))
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_players_kills
                    ON bloodstone_players (kills DESC, player_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_players_current_rampage
                    ON bloodstone_players (current_rampage DESC, player_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_players_best_rampage
                    ON bloodstone_players (best_rampage DESC, player_id)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_extra_storage_purchases (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        purchased_at TIMESTAMPTZ NOT NULL
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_guild_statistics (
                        guild_id UUID PRIMARY KEY,
                        kills INTEGER NOT NULL DEFAULT 0 CHECK (kills >= 0),
                        deaths INTEGER NOT NULL DEFAULT 0 CHECK (deaths >= 0),
                        current_rampage INTEGER NOT NULL DEFAULT 0 CHECK (current_rampage >= 0),
                        best_rampage INTEGER NOT NULL DEFAULT 0 CHECK (best_rampage >= current_rampage),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_guild_statistics_kills
                    ON bloodstone_guild_statistics (kills DESC, guild_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_guild_statistics_current_rampage
                    ON bloodstone_guild_statistics (current_rampage DESC, guild_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_guild_statistics_best_rampage
                    ON bloodstone_guild_statistics (best_rampage DESC, guild_id)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_combat_events (
                        event_id UUID PRIMARY KEY,
                        killer_id UUID NOT NULL,
                        victim_id UUID NOT NULL,
                        killer_current_rampage INTEGER NOT NULL,
                        killer_best_rampage INTEGER NOT NULL,
                        new_player_best BOOLEAN NOT NULL,
                        killer_guild_current_rampage INTEGER,
                        killer_guild_best_rampage INTEGER,
                        new_guild_best BOOLEAN NOT NULL,
                        occurred_at TIMESTAMPTZ NOT NULL,
                        processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        CHECK (
                            (killer_guild_current_rampage IS NULL
                                AND killer_guild_best_rampage IS NULL
                                AND NOT new_guild_best)
                            OR
                            (killer_guild_current_rampage IS NOT NULL
                                AND killer_guild_best_rampage IS NOT NULL)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_uncredited_death_events (
                        event_id UUID PRIMARY KEY,
                        victim_id UUID NOT NULL,
                        victim_guild_id UUID,
                        occurred_at TIMESTAMPTZ NOT NULL,
                        processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_soulbound_recoveries (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        item_payload BYTEA NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL,
                        completed_at TIMESTAMPTZ
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_soulbound_player
                    ON bloodstone_soulbound_recoveries (player_id, created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_random_box_usage (
                        player_id UUID PRIMARY KEY REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        window_start TIMESTAMPTZ,
                        free_used INTEGER NOT NULL DEFAULT 0 CHECK (free_used >= 0)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_random_box_operations (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        reward_id VARCHAR(128) NOT NULL,
                        reward_payload BYTEA NOT NULL,
                        free_use BOOLEAN NOT NULL,
                        blood_cost INTEGER NOT NULL CHECK (blood_cost >= 0),
                        created_at TIMESTAMPTZ NOT NULL,
                        completed_at TIMESTAMPTZ
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_random_box_operations_player
                    ON bloodstone_random_box_operations (player_id, created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_enchanter_offer_cooldowns (
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        offer_key VARCHAR(128) NOT NULL,
                        available_at TIMESTAMPTZ NOT NULL,
                        PRIMARY KEY (player_id, offer_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_enchanter_operations (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        original_item_payload BYTEA NOT NULL,
                        enchanted_item_payload BYTEA,
                        state VARCHAR(16) NOT NULL CHECK (state IN ('RESERVED', 'READY')),
                        created_at TIMESTAMPTZ NOT NULL,
                        completed_at TIMESTAMPTZ,
                        CHECK (
                            (state = 'RESERVED' AND enchanted_item_payload IS NULL)
                            OR (state = 'READY' AND enchanted_item_payload IS NOT NULL)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_enchanter_operations_player
                    ON bloodstone_enchanter_operations (player_id, created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_repair_operations (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        original_item_payload BYTEA NOT NULL,
                        repaired_item_payload BYTEA,
                        state VARCHAR(16) NOT NULL CHECK (state IN ('RESERVED', 'READY')),
                        created_at TIMESTAMPTZ NOT NULL,
                        completed_at TIMESTAMPTZ,
                        CHECK (
                            (state = 'RESERVED' AND repaired_item_payload IS NULL)
                            OR (state = 'READY' AND repaired_item_payload IS NOT NULL)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_repair_operations_player
                    ON bloodstone_repair_operations (player_id, created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_axe_fuser_operations (
                        operation_id UUID PRIMARY KEY,
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        original_axes_payload BYTEA NOT NULL,
                        blood_alloy_cost INTEGER NOT NULL CHECK (blood_alloy_cost > 0),
                        fused_axe_payload BYTEA,
                        state VARCHAR(16) NOT NULL CHECK (state IN ('RESERVED', 'READY')),
                        created_at TIMESTAMPTZ NOT NULL,
                        completed_at TIMESTAMPTZ,
                        CHECK (
                            (state = 'RESERVED' AND fused_axe_payload IS NULL)
                            OR (state = 'READY' AND fused_axe_payload IS NOT NULL)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_axe_fuser_operations_player
                    ON bloodstone_axe_fuser_operations (player_id, created_at)
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bloodstone_storage_contents (
                        player_id UUID NOT NULL REFERENCES bloodstone_players(player_id)
                            ON DELETE CASCADE,
                        storage_type VARCHAR(16) NOT NULL CHECK (
                            storage_type IN ('DEFAULT', 'IRON', 'GOLD', 'DIAMOND', 'EMERALD', 'EXTRA')
                        ),
                        contents_payload BYTEA,
                        version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
                        session_token UUID,
                        lease_expires_at TIMESTAMPTZ,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (player_id, storage_type),
                        CHECK (
                            (session_token IS NULL AND lease_expires_at IS NULL)
                            OR (session_token IS NOT NULL AND lease_expires_at IS NOT NULL)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_bloodstone_storage_leases
                    ON bloodstone_storage_contents (lease_expires_at)
                    WHERE session_token IS NOT NULL
                    """);
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize Bloodstone database schema", exception);
        }
    }

    private static void requireExistingTable(
            Connection connection,
            String tableName
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL"
        )) {
            statement.setString(1, "public." + tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || !resultSet.getBoolean(1)) {
                    throw new SQLException(
                            "Existing Bloodstone schema is missing required "
                                    + "table " + tableName
                                    + "; recreate the Bloodstone database"
                    );
                }
            }
        }
    }

}
