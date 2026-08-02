package net.valoury.discord.internal;

public final class InternalConstants {
    public static final String POSTGRESQL_URL = "jdbc:postgresql://localhost:5432/discord";
    public static final String POSTGRESQL_USER = "postgres";
    public static final String POSTGRESQL_PASSWORD = "postword";
    public static final int DATABASE_CONNECTION_POOL_SIZE = 5;
    public static final int DATABASE_EXECUTOR_POOL_SIZE = 5;
    public static final long DATABASE_SHUTDOWN_TIMEOUT_SECONDS = 5;
    public static final String TICKET_COLUMNS = """
            ticket_number, thread_id, owner_discord_user_id, guild_id, parent_channel_id,
            staff_role_id, status, created_at, closed_at
            """;
    public static final String ACTIVE_TICKET_STATUS_SQL = "('CREATING', 'OPEN', 'CLOSING')";
    public static final String ACCOUNT_LINK_COLUMNS =
            "minecraft_uuid, minecraft_name, discord_user_id, linked_at";

    private InternalConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
