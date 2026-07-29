package net.valoury.guilds.server;

public final class GuildsServerConstants {

    public static final String POSTGRESQL_URL =
            "jdbc:postgresql://localhost:5432/guilds";
    public static final String POSTGRESQL_USER = "postgres";
    public static final String POSTGRESQL_PASSWORD = "postword";

    public static final int DATABASE_CONNECTION_POOL_SIZE = 4;
    public static final int DATABASE_EXECUTOR_POOL_SIZE = 4;
    public static final long DATABASE_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private GuildsServerConstants() {
    }
}
