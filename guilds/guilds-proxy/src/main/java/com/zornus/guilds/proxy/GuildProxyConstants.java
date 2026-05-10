package com.zornus.guilds.proxy;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public final class GuildProxyConstants {

    public static final String POSTGRESQL_URL = "jdbc:postgresql://localhost:5432/guilds";
    public static final String POSTGRESQL_USER = "postgres";
    public static final String POSTGRESQL_PASSWORD = "postword";

    public static final int DATABASE_CONNECTION_POOL_SIZE = 10;
    public static final int DATABASE_EXECUTOR_POOL_SIZE = 10;
    public static final long DATABASE_SHUTDOWN_TIMEOUT_SECONDS = 5;

    public static final int MAX_GUILD_SIZE = 50;
    public static final int MAX_GUILD_INVITATIONS = 20;

    public static final Duration INVITATION_COOLDOWN = Duration.ofMinutes(1);
    public static final Duration CONFIRMATION_EXPIRY = Duration.ofMinutes(1);
    public static final Duration INVITATION_EXPIRY = Duration.ofMinutes(5);

    public static final Duration CLEANUP_TASK_INTERVAL = Duration.ofMinutes(15);

    public static final int MAX_MESSAGE_LENGTH = 256;

    public static final String USAGE_ACCEPT = "<red><click:suggest_command:'/guild accept '>/guild accept <guild_name></click></red>";
    public static final String USAGE_CHAT = "<red><click:suggest_command:'/guild chat '>/guild chat <message_array></click></red>";
    public static final String USAGE_INVITE = "<red><click:suggest_command:'/guild invite '>/guild invite <player_name></click></red>";
    public static final String USAGE_KICK = "<red><click:suggest_command:'/guild kick '>/guild kick <member_name></click></red>";
    public static final String USAGE_REJECT = "<red><click:suggest_command:'/guild reject '>/guild reject <guild_name></click></red>";
    public static final String USAGE_TRANSFER = "<red><click:suggest_command:'/guild transfer '>/guild transfer <member_name></click></red>";
    public static final String USAGE_UNINVITE = "<red><click:suggest_command:'/guild uninvite '>/guild uninvite <player_name></click></red>";
    public static final String USAGE_REQUESTS = "<red><click:suggest_command:'/guild requests '>/guild requests <requests_direction> [page]</click></red>";
    public static final String USAGE_SETTINGS = "<red><click:suggest_command:'/guild settings '>/guild settings [<setting> <value>]</click></red>";
    public static final String USAGE_CREATE = "<red><click:suggest_command:'/guild create '>/guild create <name> <tag></click></red>";
    public static final String USAGE_RENAME = "<red><click:suggest_command:'/guild rename '>/guild rename <new_name></click></red>";

    public static final String ERROR_NOT_IN_GUILD = "<red>You are not in a guild.</red>";
    public static final String ERROR_ALREADY_IN_GUILD = "<red>You are already in a guild. Use <yellow>/guild leave</yellow> first.</red>";
    public static final String ERROR_NOT_LEADER = "<red>Only the guild leader can perform this action.</red>";
    public static final String ERROR_SENDER_INVITATION_LIMIT_REACHED = "<red>You have reached the maximum number of pending guild invitations.</red>";
    public static final String ERROR_RECEIVER_INVITATION_LIMIT_REACHED = "<red><yellow><target></yellow> has reached the maximum number of pending guild invitations.</red>";
    public static final String ERROR_INVITATION_COOLDOWN = "<red>You must wait <yellow><time_remaining></yellow> before sending another guild invitation to <yellow><target></yellow>.</red>";
    public static final String ERROR_MESSAGE_TOO_LONG = "<red>Your message is too long. Maximum length is <yellow><max_length></yellow> characters.</red>";
    public static final String ERROR_CHAT_DISABLED = "<red>You have disabled guild chat. Use <yellow>/guild settings chat true</yellow> to enable it.</red>";
    public static final String ERROR_INVALID_GUILD_NAME = "<red>Guild name must be 3-24 characters and contain only letters, numbers, and underscores.</red>";
    public static final String ERROR_INVALID_GUILD_TAG = "<red>Guild tag must be 2-5 characters and contain only letters, numbers, and underscores.</red>";

    public static final String CREATE_SUCCESS = "<green>You have created the guild <yellow><guild_name></yellow> [<guild_tag>]!</green>";
    public static final String DISBAND_SUCCESS = "<green>Guild has been disbanded.</green>";
    public static final String DISBAND_CONFIRMATION_REQUIRED = "<yellow>Are you sure you want to disband the guild? Use <red>/guild disband confirm</red> to proceed.</yellow>";
    public static final String DISBAND_ERROR_NO_CONFIRMATION = "<red>No confirmation is pending. Use <yellow>/guild disband</yellow> first.</red>";

    public static final String LEAVE_SUCCESS = "<green>You left the guild.</green>";
    public static final String LEAVE_ERROR_NOT_IN_GUILD = "<red>You are not in a guild to leave.</red>";
    public static final String LEAVE_SUCCESS_DISBANDED = "<green>You left the guild. The guild has been disbanded as it became empty.</green>";

    public static final String INVITE_SUCCESS = "<green>Sent guild invitation to <yellow><target></yellow>!</green>";
    public static final String INVITE_ERROR_NOT_IN_GUILD = "<red>You must be in a guild to send invitations.</red>";
    public static final String INVITE_ERROR_TARGET_IN_GUILD = "<red><yellow><target></yellow> is already in a guild.</red>";
    public static final String INVITE_ERROR_TARGET_IN_ANOTHER_GUILD = "<red><yellow><target></yellow> is already in another guild.</red>";
    public static final String INVITE_ERROR_GUILD_FULL = "<red>Your guild is full! Maximum size is <maximum_size> players.</red>";
    public static final String INVITE_ERROR_ALREADY_SENT = "<red><yellow><target></yellow> has already been invited to your guild.</red>";
    public static final String INVITE_ERROR_CANNOT_INVITE_SELF = "<red>You cannot invite yourself.</red>";

    public static final String ACCEPT_SUCCESS = "<green>You joined the guild <yellow><guild_name></yellow>!</green>";
    public static final String ACCEPT_ERROR_NO_INVITATION = "<red>No guild invitation found from <yellow><guild_name></yellow>.</red>";
    public static final String ACCEPT_ERROR_GUILD_FULL = "<red>The guild is now full and you cannot join.</red>";

    public static final String REJECT_SUCCESS = "<green>Rejected guild invitation from <yellow><guild_name></yellow>.</green>";
    public static final String REJECT_ERROR_NO_INVITATION = "<red>No guild invitation found from <yellow><guild_name></yellow>.</red>";

    public static final String UNINVITE_SUCCESS = "<green>Revoked guild invitation to <yellow><target></yellow>.</green>";
    public static final String UNINVITE_ERROR_NOT_IN_GUILD = "<red>You must be in a guild to revoke invitations.</red>";
    public static final String UNINVITE_ERROR_NO_INVITATION = "<red>No pending invitation found for <yellow><target></yellow>.</red>";
    public static final String UNINVITE_ERROR_NO_PERMISSION = "<red>You do not have permission to revoke that invitation.</red>";

    public static final String KICK_SUCCESS = "<green>Kicked <yellow><target></yellow> from the guild.</green>";
    public static final String KICK_ERROR_NOT_IN_GUILD = "<red>You must be in a guild to kick members.</red>";
    public static final String KICK_ERROR_PLAYER_NOT_IN_GUILD = "<red><yellow><target></yellow> is not in your guild.</red>";
    public static final String KICK_ERROR_CANNOT_KICK_LEADER = "<red>You cannot kick the guild leader.</red>";

    public static final String TRANSFER_SUCCESS = "<green>Leadership transferred to <yellow><target></yellow>.</green>";
    public static final String TRANSFER_ERROR_NOT_IN_GUILD = "<red>You must be in a guild to transfer leadership.</red>";
    public static final String TRANSFER_ERROR_PLAYER_NOT_IN_GUILD = "<red><yellow><target></yellow> is not in your guild.</red>";
    public static final String TRANSFER_ERROR_CANNOT_TRANSFER_SELF = "<red>You cannot transfer leadership to yourself.</red>";
    public static final String TRANSFER_CONFIRMATION_REQUIRED = "<yellow>Are you sure you want to transfer leadership to <yellow><target></yellow>? Use <green>/guild transfer <target> confirm</green> to proceed.</yellow>";
    public static final String TRANSFER_ERROR_FAILED = "<red>Failed to transfer leadership.</red>";
    public static final String TRANSFER_ERROR_NO_CONFIRMATION = "<red>No confirmation is pending. Use <yellow>/guild transfer <target></yellow> first.</red>";

    public static final String RENAME_SUCCESS = "<green>Guild renamed to <yellow><new_name></yellow>.</green>";
    public static final String RENAME_ERROR_NOT_IN_GUILD = "<red>You must be in a guild to rename it.</red>";
    public static final String RENAME_ERROR_NAME_EXISTS = "<red>A guild with that name already exists.</red>";
    public static final String RENAME_CONFIRMATION_REQUIRED = "<yellow>Are you sure you want to rename the guild to <yellow><new_name></yellow>? Use <green>/guild rename <new_name> confirm</green> to proceed.</yellow>";
    public static final String RENAME_ERROR_NO_CONFIRMATION = "<red>No confirmation is pending. Use <yellow>/guild rename <name></yellow> first.</red>";

    public static final String SETTINGS_UPDATE_SUCCESS = "<green>Setting <yellow><setting></yellow> has been updated to <yellow><value></yellow>.</green>";
    public static final String SETTINGS_DISPLAY_INVITES = "<click:suggest_command:'/guild settings invites '><#2DA0ED>invites</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Who can invite you to guild: <value></white>";
    public static final String SETTINGS_DISPLAY_CHAT = "<click:suggest_command:'/guild settings chat '><#2DA0ED>chat</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Show guild chat messages: <value></white>";
    public static final String SETTINGS_ERROR_INVITES_DISABLED = "<red><yellow><target></yellow> is not accepting guild invites.</red>";
    public static final String SETTINGS_ERROR_INVITES_FRIENDS_ONLY = "<red><yellow><target></yellow> only accepts invites from friends.</red>";

    public static final String LIST_ERROR_NOT_IN_GUILD = "<red>You must be in a guild to view the member list.</red>";

    public static final String UI_LIST_MEMBER_LEADER = "<#2DA0ED><member></#2DA0ED> <#A78BFA>★</#A78BFA>";
    public static final String UI_LIST_MEMBER_NORMAL = "<#2DA0ED><member></#2DA0ED>";
    public static final String UI_LIST_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /guild list <page></gray>";
    public static final String UI_HELP_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /guild help <page></gray>";
    public static final String UI_REQUESTS_INCOMING_EMPTY = "<yellow>You do not have any incoming guild invitations.</yellow>";
    public static final String UI_REQUESTS_OUTGOING_EMPTY = "<yellow>You do not have any outgoing guild invitations.</yellow>";
    public static final String UI_REQUESTS_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /guild requests <type> <page></gray>";
    public static final String UI_REQUESTS_INCOMING_ENTRY = "<click:run_command:'/guild accept <guild_name>'><green>✔</green></click> <click:run_command:'/guild reject <guild_name>'><red>✘</red></click> <#2DA0ED><guild_name></#2DA0ED> <dark_gray>—</dark_gray> <white><timestamp></white>";
    public static final String UI_REQUESTS_OUTGOING_ENTRY = "<click:run_command:'/guild uninvite <player>'><red>✘</red></click> <#2DA0ED><player></#2DA0ED> <dark_gray>—</dark_gray> <white><timestamp></white>";

    public static final String NOTIFICATION_MEMBER_JOINED = "<green><sender> joined the guild.</green>";
    public static final String NOTIFICATION_MEMBER_LEFT = "<yellow><sender> left the guild.</yellow>";
    public static final String NOTIFICATION_MEMBER_KICKED = "<red><member> was kicked from the guild by <kicker>.</red>";
    public static final String NOTIFICATION_YOU_WERE_KICKED = "<red>You were kicked from the guild by <kicker>.</red>";
    public static final String NOTIFICATION_LEADERSHIP_TRANSFERRED = "<yellow><sender> transferred leadership to <member>.</yellow>";
    public static final String NOTIFICATION_INVITE_RECEIVED = "<green><player> invited you to join their guild <guild>.</green>";
    public static final String NOTIFICATION_INVITE_SENT_ANNOUNCEMENT = "<green><sender> invited <target> to the guild.</green>";
    public static final String NOTIFICATION_GUILD_DISBANDED = "<red>The guild has been disbanded by <leader>.</red>";
    public static final String NOTIFICATION_GUILD_RENAMED = "<yellow>The guild has been renamed from <old_name> to <new_name>.</yellow>";
    public static final String NOTIFICATION_CHAT_FORMAT = "<dark_aqua>[Guild] <sender>: <message></dark_aqua>";

    public static final List<String> HELP_COMMANDS = Arrays.asList(
            "<click:suggest_command:'/guild help '><#2DA0ED>help [page]</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Shows this help menu</white>",
            "<click:suggest_command:'/guild create '><#2DA0ED>create <name> <tag></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Creates a new guild</white>",
            "<click:suggest_command:'/guild disband'><#2DA0ED>disband</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Disbands your guild</white>",
            "<click:suggest_command:'/guild invite '><#2DA0ED>invite <player></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Sends a guild invitation</white>",
            "<click:suggest_command:'/guild uninvite '><#2DA0ED>uninvite <player></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Cancels a pending invitation</white>",
            "<click:suggest_command:'/guild accept '><#2DA0ED>accept <guild></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Accepts a guild invitation</white>",
            "<click:suggest_command:'/guild reject '><#2DA0ED>reject <guild></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Rejects a guild invitation</white>",
            "<click:suggest_command:'/guild leave'><#2DA0ED>leave</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Leaves your current guild</white>",
            "<click:suggest_command:'/guild kick '><#2DA0ED>kick <member></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Kicks a member</white>",
            "<click:suggest_command:'/guild transfer '><#2DA0ED>transfer <member></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Transfers leadership</white>",
            "<click:suggest_command:'/guild rename '><#2DA0ED>rename <name></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Renames the guild</white>",
            "<click:suggest_command:'/guild list '><#2DA0ED>list [page]</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Lists guild members</white>",
            "<click:suggest_command:'/guild requests '><#2DA0ED>requests <direction> [page]</#2DA0ED></click> <dark_gray>—</dark_gray> <white>View guild invitations</white>",
            "<click:suggest_command:'/guild chat '><#2DA0ED>chat <message></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Chat with guild members</white>",
            "<click:suggest_command:'/guild info'><#2DA0ED>info</#2DA0ED></click> <dark_gray>—</dark_gray> <white>View guild information</white>",
            "<click:suggest_command:'/guild settings '><#2DA0ED>settings [<setting> <value>]</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Manage guild preferences</white>"
    );

    private GuildProxyConstants() {
    }
}

