package net.valoury.punishments.proxy;

import java.time.Duration;
import java.util.List;

public final class PunishmentProxyConstants {
    public static final String COMMAND_PERMISSION = "valoury.punishments.manage";
    public static final String POSTGRESQL_URL = "jdbc:postgresql://localhost:5432/punishments";
    public static final String POSTGRESQL_USER = "postgres";
    public static final String POSTGRESQL_PASSWORD = "postword";
    public static final int DATABASE_CONNECTION_POOL_SIZE = 10;
    public static final int DATABASE_EXECUTOR_POOL_SIZE = 10;
    public static final long DATABASE_SHUTDOWN_TIMEOUT_SECONDS = 5;
    public static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);
    public static final String IDENTIFIER_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static final String CONSOLE_NAME = "Console";
    public static final String UNKNOWN_PLAYER = "Unknown";
    public static final String DEFAULT_KICK_REASON = "No reason specified";
    public static final String EXPIRED_REASON = "Expired";
    public static final String PERMANENT = "Permanent";
    public static final String HISTORY_DATE_FORMAT = "d/M/yy HH:mm";
    public static final String CHECK_DATE_FORMAT = "d/M/yy HH:mm:ss";

    public static final String USAGE_IMPOSE_BAN = "<red><click:suggest_command:'/punishment impose ban '>/punishment impose ban <player> <duration> <reason></click></red>";
    public static final String USAGE_IMPOSE_MUTE = "<red><click:suggest_command:'/punishment impose mute '>/punishment impose mute <player> <duration> <reason></click></red>";
    public static final String USAGE_IMPOSE_WARN = "<red><click:suggest_command:'/punishment impose warn '>/punishment impose warn <player> <duration> <reason></click></red>";
    public static final String USAGE_IMPOSE_KICK = "<red><click:suggest_command:'/punishment impose kick '>/punishment impose kick <player> [reason]</click></red>";
    public static final String USAGE_IMPOSE_PRESET = "<red><click:suggest_command:'/punishment impose preset '>/punishment impose preset <player> <preset></click></red>";
    public static final String USAGE_REVOKE_BAN = "<red><click:suggest_command:'/punishment revoke ban '>/punishment revoke ban <player> <reason></click></red>";
    public static final String USAGE_REVOKE_MUTE = "<red><click:suggest_command:'/punishment revoke mute '>/punishment revoke mute <player> <reason></click></red>";
    public static final String USAGE_REVOKE_ID = "<red><click:suggest_command:'/punishment revoke id '>/punishment revoke id <punishment_id> <reason></click></red>";
    public static final String USAGE_HISTORY = "<red><click:suggest_command:'/punishment history '>/punishment history <player_name> [page]</click></red>";
    public static final String USAGE_CHECK_BAN = "<red><click:suggest_command:'/punishment check ban '>/punishment check ban <player_name></click></red>";
    public static final String USAGE_CHECK_MUTE = "<red><click:suggest_command:'/punishment check mute '>/punishment check mute <player_name></click></red>";
    public static final String USAGE_CHECK_ID = "<red><click:suggest_command:'/punishment check id '>/punishment check id <punishment_id></click></red>";

    public static final String ERROR_INVALID_IDENTIFIER = "<red>Invalid punishment ID.</red>";
    public static final String ERROR_INVALID_DURATION = "<red>Invalid duration format. Use <yellow>permanent</yellow> or a combination of d, h, m, s (e.g., 1d2h30m).</red>";
    public static final String ERROR_CANNOT_PUNISH_SELF = "<red>You cannot punish yourself.</red>";
    public static final String ERROR_PUNISHMENT_NOT_FOUND = "<red>Punishment not found or is inactive.</red>";
    public static final String ERROR_PLAYER_ALREADY_BANNED = "<red><yellow><target></yellow> is already banned.</red>";
    public static final String PLAYER_NOT_BANNED = "<green><yellow><target></yellow> is not banned.</green>";
    public static final String ERROR_PLAYER_ALREADY_MUTED = "<red><yellow><target></yellow> is already muted.</red>";
    public static final String ERROR_PLAYER_ALREADY_WARNED_FOR_REASON = "<red><yellow><target></yellow> already has an active warning for <yellow><reason></yellow>.</red>";
    public static final String ERROR_PRESET_NOT_FOUND = "<red>Punishment preset <yellow><preset></yellow> does not exist.</red>";
    public static final String PLAYER_NOT_MUTED = "<green><yellow><target></yellow> is not muted.</green>";

    public static final String IMPOSE_SUCCESS_BAN = "<green>Successfully banned <yellow><target></yellow> (ID: <yellow>#<id></yellow>).</green>";
    public static final String IMPOSE_SUCCESS_MUTE = "<green>Successfully muted <yellow><target></yellow> (ID: <yellow>#<id></yellow>).</green>";
    public static final String IMPOSE_SUCCESS_WARN = "<green>Successfully warned <yellow><target></yellow> (ID: <yellow>#<id></yellow>).</green>";
    public static final String IMPOSE_SUCCESS_KICK = "<green>Successfully kicked <yellow><target></yellow> (ID: <yellow>#<id></yellow>).</green>";
    public static final String IMPOSE_SUCCESS_PRESET = "<green>Applied preset <yellow><preset></yellow> step <yellow><step></yellow> to <yellow><target></yellow>: <yellow><type></yellow> (ID: <yellow>#<id></yellow>).</green>";
    public static final String REVOKE_SUCCESS = "<green>Successfully revoked punishment <yellow>#<punishment_id></yellow> for <yellow><target></yellow>.</green>";
    public static final String REVOKE_SUCCESS_BAN = "<green>Successfully unbanned <yellow><target></yellow>.</green>";
    public static final String REVOKE_SUCCESS_MUTE = "<green>Successfully unmuted <yellow><target></yellow>.</green>";

    public static final String NOTIFICATION_BAN_VICTIM = "<red>You have been banned. Reason: <reason>. ID: #<id></red>";
    public static final String NOTIFICATION_MUTE_VICTIM = "<red>You have been muted. Reason: <reason>. ID: #<id></red>";
    public static final String NOTIFICATION_WARN_VICTIM = "<yellow>You have been warned. Reason: <reason>. ID: #<id></yellow>";
    public static final String NOTIFICATION_KICK_VICTIM = "<red>You have been kicked. Reason: <reason></red>";
    public static final String NOTIFICATION_BAN_PUBLIC = "<gray><punisher> banned <target>. Reason: <reason>.</gray>";
    public static final String NOTIFICATION_MUTE_PUBLIC = "<gray><punisher> muted <target>. Reason: <reason>.</gray>";
    public static final String NOTIFICATION_WARN_PUBLIC = "<gray><punisher> warned <target>. Reason: <reason>.</gray>";
    public static final String NOTIFICATION_KICK_PUBLIC = "<gray><punisher> kicked <target>. Reason: <reason>.</gray>";
    public static final String ENFORCEMENT_MUTED = "<red>You are muted and cannot speak.</red>";
    public static final String ENFORCEMENT_BANNED = "<red>You are banned from this server.</red>";

    public static final String CHECK_PLAYER_BANNED = "<red><yellow><target></yellow> is banned until <yellow><expires></yellow>. Reason: <yellow><reason></yellow>. ID: <yellow>#<id></yellow></red>";
    public static final String CHECK_PLAYER_MUTED = "<red><yellow><target></yellow> is muted until <yellow><expires></yellow>. Reason: <yellow><reason></yellow>. ID: <yellow>#<id></yellow></red>";
    public static final String UI_CHECK_DETAIL_ENTRY = "<#2DA0ED><key></#2DA0ED> <dark_gray>─</dark_gray> <white><value></white>";
    public static final String UI_HISTORY_INDICATOR_ACTIVE = "<green>●</green>";
    public static final String UI_HISTORY_INDICATOR_EXPIRED = "<gray>●</gray>";
    public static final String UI_HISTORY_INDICATOR_REVOKED = "<red>●</red>";
    public static final String UI_HISTORY_ENTRY = "<indicator> <gray>[<date>]</gray> <#2DA0ED>#<id></#2DA0ED> <white><type></white> <dark_gray>─</dark_gray> <white><reason></white>";
    public static final String UI_HISTORY_EMPTY = "<yellow>No punishment history found.</yellow>";
    public static final String UI_HISTORY_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /punishment history <target> <page></gray>";

    public static final String UI_HELP_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /punishment help <page></gray>";
    public static final String UI_HELP_PAGINATION_IMPOSE = "<gray>Page <current_page>/<maximum_pages> - /punishment impose help <page></gray>";
    public static final String UI_HELP_PAGINATION_REVOKE = "<gray>Page <current_page>/<maximum_pages> - /punishment revoke help <page></gray>";
    public static final String UI_HELP_PAGINATION_CHECK = "<gray>Page <current_page>/<maximum_pages> - /punishment check help <page></gray>";
    public static final List<String> HELP_COMMANDS = List.of(
            "<click:suggest_command:'/punishment help '><#2DA0ED>help [page]</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Shows this help menu</white>",
            "<click:suggest_command:'/punishment impose '><#2DA0ED>impose ...</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Impose a punishment</white>",
            "<click:suggest_command:'/punishment revoke '><#2DA0ED>revoke ...</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Revoke a punishment</white>",
            "<click:suggest_command:'/punishment check '><#2DA0ED>check ...</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Check a punishment or player status</white>",
            "<click:suggest_command:'/punishment history '><#2DA0ED>history <player> [page]</#2DA0ED></click> <dark_gray>─</dark_gray> <white>View a player's history</white>"
    );
    public static final List<String> HELP_COMMANDS_IMPOSE = List.of(
            "<click:suggest_command:'/punishment impose help '><#2DA0ED>help [page]</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Shows this help menu</white>",
            "<click:suggest_command:'/punishment impose ban '><#2DA0ED>ban <player> <duration> <reason></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Ban a player</white>",
            "<click:suggest_command:'/punishment impose mute '><#2DA0ED>mute <player> <duration> <reason></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Mute a player</white>",
            "<click:suggest_command:'/punishment impose warn '><#2DA0ED>warn <player> <duration> <reason></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Warn a player</white>",
            "<click:suggest_command:'/punishment impose kick '><#2DA0ED>kick <player> [reason]</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Kick a player</white>",
            "<click:suggest_command:'/punishment impose preset '><#2DA0ED>preset <player> <preset></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Apply a punishment preset</white>"
    );
    public static final List<String> HELP_COMMANDS_REVOKE = List.of(
            "<click:suggest_command:'/punishment revoke help '><#2DA0ED>help [page]</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Shows this help menu</white>",
            "<click:suggest_command:'/punishment revoke ban '><#2DA0ED>ban <player> <reason></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Unban a player</white>",
            "<click:suggest_command:'/punishment revoke mute '><#2DA0ED>mute <player> <reason></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Unmute a player</white>",
            "<click:suggest_command:'/punishment revoke id '><#2DA0ED>id <id> <reason></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Revoke a specific punishment</white>"
    );
    public static final List<String> HELP_COMMANDS_CHECK = List.of(
            "<click:suggest_command:'/punishment check help '><#2DA0ED>help [page]</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Shows this help menu</white>",
            "<click:suggest_command:'/punishment check ban '><#2DA0ED>ban <player></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Check a player's ban status</white>",
            "<click:suggest_command:'/punishment check mute '><#2DA0ED>mute <player></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Check a player's mute status</white>",
            "<click:suggest_command:'/punishment check id '><#2DA0ED>id <id></#2DA0ED></click> <dark_gray>─</dark_gray> <white>Check a specific punishment</white>"
    );

    private PunishmentProxyConstants() {
    }
}
