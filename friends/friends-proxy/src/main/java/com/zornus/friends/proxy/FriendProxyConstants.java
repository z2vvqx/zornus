package com.zornus.friends.proxy;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public final class FriendProxyConstants {

    private FriendProxyConstants() {
    }

    public static final String POSTGRESQL_URL = "jdbc:postgresql://localhost:5432/friends";
    public static final String POSTGRESQL_USER = "postgres";
    public static final String POSTGRESQL_PASSWORD = "postword";
    public static final int DATABASE_CONNECTION_POOL_SIZE = 10;
    public static final int DATABASE_EXECUTOR_POOL_SIZE = 10;
    public static final int DATABASE_SHUTDOWN_TIMEOUT_SECONDS = 5;
    public static final int MAX_FRIENDS = 50;
    public static final int MAX_FRIEND_REQUESTS = 50;
    public static final int MAX_MESSAGE_LENGTH = 256;
    public static final Duration REQUEST_EXPIRY_DURATION = Duration.ofDays(7);
    public static final Duration COOLDOWN_EXPIRY_DURATION = Duration.ofDays(1);
    public static final Duration CLEANUP_TASK_INTERVAL = Duration.ofMinutes(30);
    public static final Duration FRIEND_REQUEST_COOLDOWN = Duration.ofMinutes(1);
    public static final Duration LAST_SEEN_RETENTION = Duration.ofDays(30);
    public static final Duration LAST_MESSAGE_SENDER_RETENTION = Duration.ofDays(1);

    public static final String USAGE_ADD = "<red><click:suggest_command:'/friend add '>/friend add <player_name></click></red>";
    public static final String USAGE_ACCEPT = "<red><click:suggest_command:'/friend accept '>/friend accept <player_name></click></red>";
    public static final String USAGE_REJECT = "<red><click:suggest_command:'/friend reject '>/friend reject <player_name></click></red>";
    public static final String USAGE_REMOVE = "<red><click:suggest_command:'/friend remove '>/friend remove <friend_name></click></red>";
    public static final String USAGE_REVOKE = "<red><click:suggest_command:'/friend revoke '>/friend revoke <player_name></click></red>";
    public static final String USAGE_MESSAGE = "<red><click:suggest_command:'/friend message '>/friend message <friend_name> <message_array></click></red>";
    public static final String USAGE_REPLY = "<red><click:suggest_command:'/friend reply '>/friend reply <message_array></click></red>";
    public static final String USAGE_JUMP = "<red><click:suggest_command:'/friend jump '>/friend jump <friend_name></click></red>";
    public static final String USAGE_REQUESTS = "<red><click:suggest_command:'/friend requests '>/friend requests <requests_direction> [page]</click></red>";
    public static final String USAGE_PRESENCE = "<red><click:suggest_command:'/friend presence '>/friend presence <presence_state></click></red>";
    public static final String USAGE_SETTINGS = "<red><click:suggest_command:'/friend settings '>/friend settings [<setting_value> <boolean_value>]</click></red>";

    public static final String ERROR_CANNOT_PERFORM_ON_SELF = "<red>You cannot send a friend request to yourself.</red>";
    public static final String ERROR_SENDER_FRIENDS_LIMIT_REACHED = "<red>You have reached the maximum number of friends.</red>";
    public static final String ERROR_RECEIVER_FRIENDS_LIMIT_REACHED = "<red><yellow><target></yellow> has reached the maximum number of friends.</red>";
    public static final String ERROR_ALREADY_FRIENDS = "<red>You are already friends with <yellow><target></yellow>.</red>";
    public static final String ERROR_NOT_FRIENDS = "<red>You are not friends with <yellow><target></yellow>.</red>";
    public static final String ERROR_NO_LONGER_FRIENDS_REPLY = "<red>You are no longer friends with <yellow><target></yellow>.</red>";
    public static final String ERROR_FRIEND_OFFLINE = "<red><yellow><target></yellow> is not online.</red>";
    public static final String ERROR_PLAYER_NOT_ACCEPTING_REQUESTS = "<red><yellow><target></yellow> is not accepting friend requests.</red>";
    public static final String ERROR_PLAYER_NOT_ACCEPTING_MESSAGES = "<red><yellow><target></yellow> is not accepting messages.</red>";
    public static final String ERROR_PLAYER_NOT_ALLOWING_JUMP = "<red><yellow><target></yellow> is not allowing friend to jump to them.</red>";
    public static final String ERROR_MESSAGE_TOO_LONG = "<red>Your message is too long. Maximum length is <yellow><max_length></yellow> characters.</red>";
    public static final String ERROR_SENDER_REQUEST_LIMIT_REACHED = "<red>You have reached the maximum number of pending friend requests.</red>";
    public static final String ERROR_RECEIVER_REQUEST_LIMIT_REACHED = "<red><yellow><target></yellow> has reached the maximum number of pending friend requests.</red>";
    public static final String ERROR_REQUEST_COOLDOWN = "<red>You must wait <yellow><time_remaining></yellow> before sending another friend request to <yellow><target></yellow>.</red>";
    public static final String ERROR_INVALID_SETTING = "<red>Unknown setting '<yellow><setting></yellow>'. Valid settings: messaging, jumping, lastseen, location, requests.</red>";

    public static final String REQUEST_ADD_SUCCESS = "<green>Friend request sent to <yellow><target></yellow>!</green>";
    public static final String REQUEST_ACCEPT_SUCCESS = "<green>You are now friends with <yellow><target></yellow>!</green>";
    public static final String REQUEST_ACCEPT_SUCCESS_AUTO = "<green>You and <yellow><target></yellow> are now friends! (Request accepted automatically)</green>";
    public static final String REQUEST_REJECT_SUCCESS = "<green>Friend request from <yellow><target></yellow> has been rejected.</green>";
    public static final String REQUEST_REVOKE_SUCCESS = "<green>Friend request to <yellow><target></yellow> has been revoked.</green>";
    public static final String REQUEST_ERROR_ALREADY_SENT = "<red>You have already sent a friend request to <yellow><target></yellow>.</red>";
    public static final String REQUEST_ERROR_NOT_FOUND = "<red>No friend request found between you and <yellow><target></yellow>.</red>";

    public static final String REMOVE_SUCCESS = "<green><yellow><target></yellow> has been removed from your friend list.</green>";

    public static final String PRESENCE_DISPLAY = "<gray>Your presence is set to: <yellow><presence></yellow></gray>";
    public static final String PRESENCE_UPDATE_SUCCESS = "<green>Your presence has been set to <yellow><presence></yellow>.</green>";

    public static final String SETTINGS_UPDATE_SUCCESS = "<green>Setting <yellow><setting></yellow> has been updated to <yellow><value></yellow>.</green>";
    public static final String SETTINGS_DISPLAY_ALLOW_MESSAGES = "<click:suggest_command:'/friend settings messaging '><#2DA0ED>messaging</#2DA0ED></click> <dark_gray>-</dark_gray> <white>Allow friend to message you: <value></white>";
    public static final String SETTINGS_DISPLAY_ALLOW_JUMP = "<click:suggest_command:'/friend settings jumping '><#2DA0ED>jumping</#2DA0ED></click> <dark_gray>-</dark_gray> <white>Allow friend to jump into your instance: <value></white>";
    public static final String SETTINGS_DISPLAY_SHOW_LAST_SEEN = "<click:suggest_command:'/friend settings lastseen '><#2DA0ED>lastseen</#2DA0ED></click> <dark_gray>-</dark_gray> <white>Show your last seen timestamp: <value></white>";
    public static final String SETTINGS_DISPLAY_SHOW_LOCATION = "<click:suggest_command:'/friend settings location '><#2DA0ED>location</#2DA0ED></click> <dark_gray>-</dark_gray> <white>Show your current location: <value></white>";
    public static final String SETTINGS_DISPLAY_ALLOW_REQUESTS = "<click:suggest_command:'/friend settings requests '><#2DA0ED>requests</#2DA0ED></click> <dark_gray>-</dark_gray> <white>Accept incoming friend requests: <value></white>";

    public static final String MESSAGE_SENT_FORMAT = "<gray>[To <yellow><target></yellow>] <white><message></white></gray>";
    public static final String MESSAGE_RECEIVED_FORMAT = "<gray>[From <yellow><sender></yellow>] <white><message></white></gray>";
    public static final String MESSAGE_ERROR_NO_REPLY_TARGET = "<red>No recent friend messages to reply to.</red>";
    public static final String MESSAGE_REPLY_SUCCESS = "<green>Reply sent!</green>";

    public static final String JUMP_SUCCESS = "<green>Teleported to <yellow><target></yellow>!</green>";
    public static final String JUMP_ERROR_NO_INSTANCE = "<red><yellow><target></yellow> is not in a valid instance.</red>";
    public static final String JUMP_INFO_SAME_INSTANCE = "<yellow>You are already in the same instance as <yellow><target></yellow>.</yellow>";

    public static final String UI_LIST_EMPTY = "<yellow>You don't have any friends yet. Use <green>/friend add <player></green> to send a friend request!</yellow>";
    public static final String UI_LIST_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /friend list <page></gray>";
    public static final String UI_STATUS_ONLINE = "<green>●</green> <#2DA0ED><friend></#2DA0ED> <gray>(Online)</gray>";
    public static final String UI_STATUS_ONLINE_WITH_LOCATION = "<green>●</green> <#2DA0ED><friend></#2DA0ED> <dark_gray>-</dark_gray> <gray>Playing on <click:suggest_command:'/friend jump <friend>'><yellow><server></yellow></click></gray>";
    public static final String UI_STATUS_OFFLINE = "<red>●</red> <#2DA0ED><friend></#2DA0ED> <dark_gray>-</dark_gray> <gray>Last seen <timestamp></gray>";
    public static final String UI_STATUS_OFFLINE_NO_DATA = "<red>●</red> <dark_gray><friend></dark_gray> <gray>(Offline)</gray>";
    public static final String UI_HELP_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /friend help <page></gray>";
    public static final String UI_REQUESTS_INCOMING_EMPTY = "<yellow>You don't have any incoming friend requests.</yellow>";
    public static final String UI_REQUESTS_OUTGOING_EMPTY = "<yellow>You don't have any outgoing friend requests.</yellow>";
    public static final String UI_REQUESTS_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /friend requests <type> <page></gray>";
    public static final String UI_REQUESTS_INCOMING_ENTRY = "<click:run_command:'/friend accept <player>'><green>✔</green></click> <click:run_command:'/friend reject <player>'><red>✘</red></click> <#2DA0ED><player></#2DA0ED> <dark_gray>-</dark_gray> <white><timestamp></white>";
    public static final String UI_REQUESTS_OUTGOING_ENTRY = "<click:run_command:'/friend revoke <player>'><red>✘</red></click> <#2DA0ED><player></#2DA0ED> <dark_gray>-</dark_gray> <white><timestamp></white>";
    public static final String UI_REQUESTS_ENTRY = "<#2DA0ED><player></#2DA0ED> <dark_gray>-</dark_gray> <white><timestamp></white>";

    public static final String NOTIFICATION_REQUEST_RECEIVED = "<green>You received a friend request from <yellow><sender></yellow>! <click:run_command:'/friend accept <sender>'><green>✔</green></click> <click:run_command:'/friend reject <sender>'><red>✘</red></click></green>";
    public static final String NOTIFICATION_REQUEST_ACCEPTED = "<green><yellow><sender></yellow> accepted your friend request! You are now friends.</green>";
    public static final String NOTIFICATION_FRIEND_JOINED = "<green><yellow><friend></yellow> is now online!</green>";
    public static final String NOTIFICATION_FRIEND_LEFT = "<red><yellow><friend></yellow> is now offline.</red>";

    public static final List<String> HELP_COMMANDS = Arrays.asList(
            "<click:suggest_command:'/friend help '><#2DA0ED>help [page]</#2DA0ED></click> <dark_gray>-</dark_gray> <white>Shows this help menu</white>",
            "<click:suggest_command:'/friend list '><#2DA0ED>list [page]</#2DA0ED></click> <dark_gray>-</dark_gray> <white>List your friends</white>",
            "<click:suggest_command:'/friend add '><#2DA0ED>add <player></#2DA0ED></click> <dark_gray>-</dark_gray> <white>Send a friend request</white>",
            "<click:suggest_command:'/friend revoke '><#2DA0ED>revoke <player></#2DA0ED></click> <dark_gray>-</dark_gray> <white>Revoke a friend request</white>",
            "<click:suggest_command:'/friend accept '><#2DA0ED>accept <player></#2DA0ED></click> <dark_gray>-</dark_gray> <white>Accept a friend request</white>",
            "<click:suggest_command:'/friend reject '><#2DA0ED>reject <player></#2DA0ED></click> <dark_gray>-</dark_gray> <white>Reject a friend request</white>",
            "<click:suggest_command:'/friend remove '><#2DA0ED>remove <friend></#2DA0ED></click> <dark_gray>-</dark_gray> <white>Remove a friend</white>",
            "<click:suggest_command:'/friend requests '><#2DA0ED>requests <direction> [page]</#2DA0ED></click> <dark_gray>-</dark_gray> <white>View friend requests</white>",
            "<click:suggest_command:'/friend message '><#2DA0ED>message <friend> <message></#2DA0ED></click> <dark_gray>-</dark_gray> <white>Contact a friend</white>",
            "<click:suggest_command:'/friend reply '><#2DA0ED>reply <message></#2DA0ED></click> <dark_gray>-</dark_gray> <white>Reply to the last friend message</white>",
            "<click:suggest_command:'/friend jump '><#2DA0ED>jump <friend></#2DA0ED></click> <dark_gray>-</dark_gray> <white>Jump to a friend's instance</white>",
            "<click:suggest_command:'/friend presence '><#2DA0ED>presence [state]</#2DA0ED></click> <dark_gray>-</dark_gray> <white>Set your online presence visibility</white>",
            "<click:suggest_command:'/friend settings '><#2DA0ED>settings [<setting> <boolean>]</#2DA0ED></click> <dark_gray>-</dark_gray> <white>Configure friend settings</white>"
    );

}
