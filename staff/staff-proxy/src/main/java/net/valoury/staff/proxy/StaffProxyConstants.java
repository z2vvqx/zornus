package net.valoury.staff.proxy;

import java.time.Duration;
import java.util.List;

public final class StaffProxyConstants {
    public static final String COMMAND_PERMISSION = "valoury.command.staff";
    public static final String INSPECT_COMMAND_PERMISSION = COMMAND_PERMISSION + ".inspect";
    public static final String CONNECTIONS_COMMAND_PERMISSION = COMMAND_PERMISSION + ".connections";
    public static final String RELATED_COMMAND_PERMISSION = COMMAND_PERMISSION + ".related";
    public static final String ADDRESS_HMAC_KEY =
            "RkFLRV9TVEFGRl9BRERSRVNTX0hNQUNfS0VZXzAwMDA=";

    public static final String POSTGRESQL_URL = "jdbc:postgresql://localhost:5432/staff";
    public static final String POSTGRESQL_USER = "postgres";
    public static final String POSTGRESQL_PASSWORD = "postword";
    public static final int DATABASE_CONNECTION_POOL_SIZE = 5;
    public static final int DATABASE_EXECUTOR_POOL_SIZE = 5;
    public static final long DATABASE_SHUTDOWN_TIMEOUT_SECONDS = 5;

    public static final int CONNECTION_RETENTION_DAYS = 30;
    public static final Duration CONNECTION_RETENTION =
            Duration.ofDays(CONNECTION_RETENTION_DAYS);
    public static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);
    public static final int MINIMUM_ADDRESS_HMAC_KEY_BYTES = 32;
    public static final int DISPLAY_FINGERPRINT_CHARACTERS = 12;

    public static final String USAGE_INSPECT =
            "<red><click:suggest_command:'/staff inspect '>/staff inspect <player></click></red>";
    public static final String USAGE_CONNECTIONS =
            "<red><click:suggest_command:'/staff connections '>/staff connections <player> [page]</click></red>";
    public static final String USAGE_RELATED =
            "<red><click:suggest_command:'/staff related '>/staff related "
                    + "<player> [page|IP-ID [page]]</click></red>";

    public static final String UI_DETAIL_ENTRY =
            "<#2DA0ED><key></#2DA0ED> <dark_gray>─</dark_gray> <white><value></white>";
    public static final String UI_CONNECTION_ENTRY =
            "<#2DA0ED><identifier></#2DA0ED> <dark_gray>─</dark_gray> "
                    + "<white><connections> connection(s), <accounts></white>";
    public static final String UI_ENTRY_TIMESPAN =
            "   <gray><first_seen> <dark_gray>─</dark_gray> <last_seen></gray>";
    public static final String UI_RELATED_DIRECT_ENTRY =
            "<#2DA0ED><target></#2DA0ED> "
                    + "<dark_gray>─</dark_gray> "
                    + "<white>Direct, <connections> shared connection(s)</white>";
    public static final String UI_RELATED_INDIRECT_ENTRY =
            "<#2DA0ED><target></#2DA0ED> "
                    + "<dark_gray>─</dark_gray> "
                    + "<white>Indirect via <via> (<depth> hops)</white>";
    public static final String UI_CONNECTIONS_EMPTY =
            "<yellow>No retained connections were found for this player.</yellow>";
    public static final String UI_RELATED_EMPTY =
            "<yellow>No related accounts were found for this player.</yellow>";
    public static final String UI_RELATED_CONNECTION_EMPTY =
            "<yellow>No other accounts were found for this connection.</yellow>";
    public static final String UI_RELATED_CONNECTION_NOT_FOUND =
            "<red>That connection identifier is not retained for this player.</red>";
    public static final String UI_RELATED_CONNECTION_AMBIGUOUS =
            "<red>That shortened connection identifier is ambiguous.</red>";
    public static final String UI_CONNECTIONS_PAGINATION =
            "<gray>Page <current_page>/<maximum_pages> - /staff connections <target> <page></gray>";
    public static final String UI_RELATED_PAGINATION =
            "<gray>Page <current_page>/<maximum_pages> - /staff related <target> <page></gray>";
    public static final String UI_RELATED_CONNECTION_PAGINATION =
            "<gray>Page <current_page>/<maximum_pages> - /staff related "
                    + "<target> <identifier> <page></gray>";
    public static final String UI_HELP_PAGINATION =
            "<gray>Page <current_page>/<maximum_pages> - /staff help <page></gray>";

    public static final List<String> HELP_COMMANDS = List.of(
            "<click:suggest_command:'/staff inspect '><#2DA0ED>inspect <player></#2DA0ED></click> "
                    + "<dark_gray>─</dark_gray> <white>View a player's connection overview</white>",
            "<click:suggest_command:'/staff connections '><#2DA0ED>connections <player> [page]</#2DA0ED></click> "
                    + "<dark_gray>─</dark_gray> <white>View a player's retained connections</white>",
            "<click:suggest_command:'/staff related '><#2DA0ED>related <player> [page]</#2DA0ED></click> "
                    + "<dark_gray>─</dark_gray> <white>View directly and indirectly related accounts</white>"
    );

    private StaffProxyConstants() {
    }
}
