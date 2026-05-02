package com.zornus.parties.proxy;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public final class PartyProxyConstants {

    public static final String POSTGRESQL_URL = "jdbc:postgresql://localhost:5432/parties";
    public static final String POSTGRESQL_USER = "postgres";
    public static final String POSTGRESQL_PASSWORD = "postword";

    public static final int DATABASE_CONNECTION_POOL_SIZE = 10;
    public static final int DATABASE_EXECUTOR_POOL_SIZE = 10;
    public static final long DATABASE_SHUTDOWN_TIMEOUT_SECONDS = 5;

    public static final int MAX_PARTY_SIZE = 8;
    public static final int MAX_PARTY_INVITATIONS = 20;

    public static final Duration INVITATION_COOLDOWN = Duration.ofMinutes(1);
    public static final Duration CONFIRMATION_EXPIRY = Duration.ofMinutes(1);
    public static final Duration INVITATION_EXPIRY = Duration.ofMinutes(2).plus(Duration.ofSeconds(30));
    public static final Duration WARP_COOLDOWN = Duration.ofSeconds(30);

    public static final int MAX_MESSAGE_LENGTH = 256;

    // ========================================
    // COMMAND USAGE
    // ========================================

    public static final String USAGE_ACCEPT = "<red><click:suggest_command:'/party accept '>/party accept <leader_name></click></red>";
    public static final String USAGE_CHAT = "<red><click:suggest_command:'/party chat '>/party chat <message_array></click></red>";
    public static final String USAGE_INVITE = "<red><click:suggest_command:'/party invite '>/party invite <player_name></click></red>";
    public static final String USAGE_KICK = "<red><click:suggest_command:'/party kick '>/party kick <member_name> [reason_array]</click></red>";
    public static final String USAGE_REJECT = "<red><click:suggest_command:'/party reject '>/party reject <leader_name></click></red>";
    public static final String USAGE_TRANSFER = "<red><click:suggest_command:'/party transfer '>/party transfer <member_name></click></red>";
    public static final String USAGE_UNINVITE = "<red><click:suggest_command:'/party uninvite '>/party uninvite <player_name></click></red>";
    public static final String USAGE_REQUESTS = "<red><click:suggest_command:'/party requests '>/party requests <requests_direction> [page]</click></red>";
    public static final String USAGE_SETTINGS = "<red><click:suggest_command:'/party settings '>/party settings [<setting> <value>]</click></red>";

    // ========================================
    // GENERIC VALIDATION & ERRORS
    // ========================================

    public static final String ERROR_NOT_IN_PARTY = "<red>You are not in a party.</red>";
    public static final String ERROR_ALREADY_IN_PARTY = "<red>You are already in a party. Use <yellow>/party leave</yellow> first.</red>";
    public static final String ERROR_NOT_LEADER = "<red>Only the party leader can perform this action.</red>";
    public static final String ERROR_SENDER_INVITATION_LIMIT_REACHED = "<red>You have reached the maximum number of pending party invitations.</red>";
    public static final String ERROR_RECEIVER_INVITATION_LIMIT_REACHED = "<red><yellow><target></yellow> has reached the maximum number of pending party invitations.</red>";
    public static final String ERROR_INVITATION_COOLDOWN = "<red>You must wait <yellow><time_remaining></yellow> before sending another party invitation to <yellow><target></yellow>.</red>";
    public static final String ERROR_MESSAGE_TOO_LONG = "<red>Your message is too long. Maximum length is <yellow><max_length></yellow> characters.</red>";
    public static final String ERROR_CHAT_DISABLED = "<red>You have disabled party chat. Use <yellow>/party settings chat true</yellow> to enable it.</red>";

    // ========================================
    // PARTY LIFECYCLE
    // ========================================

    public static final String CREATE_SUCCESS = "<green>You have created a new party! Use <yellow>/party invite <player></yellow> to invite players.</green>";
    public static final String DISBAND_SUCCESS = "<green>Party has been disbanded.</green>";
    public static final String DISBAND_ERROR_NOT_IN_PARTY = "<red>You must be in a party to disband it.</red>";
    public static final String DISBAND_CONFIRMATION_REQUIRED = "<yellow>Are you sure you want to disband the party? Use <red>/party disband confirm</red> to proceed.</yellow>";
    public static final String DISBAND_ERROR_NO_CONFIRMATION = "<red>No confirmation is pending. Use <yellow>/party disband</yellow> first.</red>";

    public static final String LEAVE_SUCCESS = "<green>You left the party.</green>";
    public static final String LEAVE_ERROR_NOT_IN_PARTY = "<red>You are not in a party to leave.</red>";
    public static final String LEAVE_SUCCESS_DISBANDED = "<green>You left the party. The party has been disbanded as it became empty.</green>";

    // ========================================
    // PARTY INVITATIONS
    // ========================================

    public static final String INVITE_SUCCESS = "<green>Sent party invitation to <yellow><target></yellow>!</green>";
    public static final String INVITE_ERROR_NOT_IN_PARTY = "<red>You must be in a party to send invitations. Use <yellow>/party create</yellow> first.</red>";
    public static final String INVITE_ERROR_TARGET_IN_PARTY = "<red><yellow><target></yellow> is already in a party.</red>";
    public static final String INVITE_ERROR_PARTY_FULL = "<red>Your party is full! Maximum size is <maximum_size> players.</red>";
    public static final String INVITE_ERROR_ALREADY_SENT = "<red><yellow><target></yellow> has already been invited to your party.</red>";
    public static final String INVITE_ERROR_CANNOT_INVITE_SELF = "<red>You cannot invite yourself.</red>";

    public static final String ACCEPT_SUCCESS = "<green>You joined <yellow><target></yellow>'s party!</green>";
    public static final String ACCEPT_ERROR_NO_INVITATION = "<red>No party invitation found from <yellow><target></yellow>.</red>";
    public static final String ACCEPT_ERROR_PARTY_FULL = "<red>The party is now full and you cannot join.</red>";

    public static final String REJECT_SUCCESS = "<green>Rejected party invitation from <yellow><target></yellow>.</green>";
    public static final String REJECT_ERROR_NO_INVITATION = "<red>No party invitation found from <yellow><target></yellow>.</red>";

    public static final String UNINVITE_SUCCESS = "<green>Revoked party invitation to <yellow><target></yellow>.</green>";
    public static final String UNINVITE_ERROR_NOT_IN_PARTY = "<red>You must be in a party to revoke invitations.</red>";
    public static final String UNINVITE_ERROR_NO_INVITATION = "<red>No pending invitation found for <yellow><target></yellow>.</red>";
    public static final String UNINVITE_ERROR_NO_PERMISSION = "<red>You don't have permission to revoke that invitation.</red>";

    // ========================================
    // MEMBER MANAGEMENT
    // ========================================

    public static final String KICK_SUCCESS = "<green>Kicked <yellow><target></yellow> from the party.</green>";
    public static final String KICK_ERROR_NOT_IN_PARTY = "<red>You must be in a party to kick members.</red>";
    public static final String KICK_ERROR_PLAYER_NOT_IN_PARTY = "<red><yellow><target></yellow> is not in your party.</red>";
    public static final String KICK_ERROR_CANNOT_KICK_SELF = "<red>You cannot kick yourself. Use <yellow>/party leave</yellow> instead.</red>";

    public static final String TRANSFER_SUCCESS = "<green>Leadership transferred to <yellow><target></yellow>.</green>";
    public static final String TRANSFER_ERROR_NOT_IN_PARTY = "<red>You must be in a party to transfer leadership.</red>";
    public static final String TRANSFER_ERROR_PLAYER_NOT_IN_PARTY = "<red><yellow><target></yellow> is not in your party.</red>";
    public static final String TRANSFER_ERROR_CANNOT_TRANSFER_SELF = "<red>You cannot transfer leadership to yourself.</red>";
    public static final String TRANSFER_CONFIRMATION_REQUIRED = "<yellow>Are you sure you want to transfer leadership to <yellow><target></yellow>? Use <green>/party transfer <target> confirm</green> to proceed.</yellow>";
    public static final String TRANSFER_ERROR_FAILED = "<red>Failed to transfer leadership.</red>";
    public static final String TRANSFER_ERROR_NO_CONFIRMATION = "<red>No confirmation is pending. Use <yellow>/party transfer <target></yellow> first.</red>";

    // ========================================
    // SETTINGS
    // ========================================

    public static final String SETTINGS_UPDATE_SUCCESS = "<green>Setting <yellow><setting></yellow> has been updated to <yellow><value></yellow>.</green>";
    public static final String SETTINGS_DISPLAY_WARP = "<click:suggest_command:'/party settings warp '><#2DA0ED>warp</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Allow party leader to warp you: <value></white>";
    public static final String SETTINGS_DISPLAY_CHAT = "<click:suggest_command:'/party settings chat '><#2DA0ED>chat</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Show party chat messages: <value></white>";
    public static final String SETTINGS_DISPLAY_INVITES = "<click:suggest_command:'/party settings invites '><#2DA0ED>invites</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Who can invite you to party: <value></white>";
    public static final String SETTINGS_ERROR_INVITES_DISABLED = "<red><yellow><target></yellow> is not accepting party invites.</red>";
    public static final String SETTINGS_ERROR_INVITES_FRIENDS_ONLY = "<red><yellow><target></yellow> only accepts invites from friends.</red>";

    // ========================================
    // TELEPORTATION
    // ========================================

    public static final String JUMP_SUCCESS = "<green>Teleported to your party leader!</green>";
    public static final String JUMP_ERROR_NOT_IN_PARTY = "<red>You must be in a party to jump to the leader.</red>";
    public static final String JUMP_ERROR_CANNOT_JUMP_AS_LEADER = "<red>You are the party leader! Use <yellow>/party warp</yellow> to bring members to you.</red>";
    public static final String JUMP_ERROR_LEADER_NOT_ONLINE = "<red>Your party leader is not online.</red>";
    public static final String JUMP_ERROR_LEADER_NO_INSTANCE = "<red>Your party leader is not in a valid server.</red>";
    public static final String JUMP_INFO_ALREADY_WITH_LEADER = "<yellow>You are already on the same server as your leader.</yellow>";

    public static final String WARP_SUCCESS = "<green>Warped all party members to your server!</green>";
    public static final String WARP_ERROR_NOT_IN_PARTY = "<red>You must be in a party to warp members.</red>";
    public static final String WARP_ERROR_ON_COOLDOWN = "<red>Party warp is on cooldown. Please wait before using it again.</red>";
    public static final String WARP_ERROR_NO_INSTANCE = "<red>You must be on a valid server to warp party members.</red>";

    // ========================================
    // UI & DISPLAY
    // ========================================

    public static final String LIST_ERROR_NOT_IN_PARTY = "<red>You must be in a party to view the member list.</red>";

    public static final String UI_LIST_MEMBER_LEADER = "<#2DA0ED><member></#2DA0ED> <#A78BFA>★</#A78BFA>";
    public static final String UI_LIST_MEMBER_NORMAL = "<#2DA0ED><member></#2DA0ED>";
    public static final String UI_LIST_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /party list <page></gray>";
    public static final String UI_HELP_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /party help <page></gray>";
    public static final String UI_REQUESTS_INCOMING_EMPTY = "<yellow>You don't have any incoming party invitations.</yellow>";
    public static final String UI_REQUESTS_OUTGOING_EMPTY = "<yellow>You don't have any outgoing party invitations.</yellow>";
    public static final String UI_REQUESTS_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /party requests <type> <page></gray>";
    public static final String UI_REQUESTS_INCOMING_ENTRY = "<click:run_command:'/party accept <player>'><green>✔</green></click> <click:run_command:'/party reject <player>'><red>✘</red></click> <#2DA0ED><player></#2DA0ED> <dark_gray>—</dark_gray> <white><timestamp></white>";
    public static final String UI_REQUESTS_OUTGOING_ENTRY = "<click:run_command:'/party uninvite <player>'><red>✘</red></click> <#2DA0ED><player></#2DA0ED> <dark_gray>—</dark_gray> <white><timestamp></white>";
    public static final String UI_REQUESTS_ENTRY = "<#2DA0ED><player></#2DA0ED> <dark_gray>—</dark_gray> <white><timestamp></white>";

    // ========================================
    // NOTIFICATIONS
    // ========================================

    public static final String NOTIFICATION_MEMBER_JOINED = "<green><sender> joined the party.</green>";
    public static final String NOTIFICATION_MEMBER_LEFT = "<yellow><sender> left the party.</yellow>";
    public static final String NOTIFICATION_MEMBER_KICKED = "<red><member> was kicked from the party.</red>";
    public static final String NOTIFICATION_MEMBER_KICKED_WITH_REASON = "<red><member> was kicked from the party. Reason: <reason></red>";
    public static final String NOTIFICATION_MEMBER_DISCONNECTED = "<yellow><player> disconnected from the party.</yellow>";
    public static final String NOTIFICATION_LEADER_DISCONNECTED = "<yellow><old_leader> disconnected. <new_leader> is now the party leader.</yellow>";
    public static final String NOTIFICATION_LEADERSHIP_TRANSFERRED = "<yellow><sender> transferred leadership to <member>.</yellow>";
    public static final String NOTIFICATION_INVITE_RECEIVED = "<green><player> invited you to join their party.</green>";
    public static final String NOTIFICATION_INVITE_SENT_ANNOUNCEMENT = "<green><sender> invited <target> to the party.</green>";
    public static final String NOTIFICATION_MEMBER_WARPED = "<green>You were warped to <sender>'s server.</green>";
    public static final String NOTIFICATION_PARTY_DISBANDED = "<red>The party has been disbanded by <leader>.</red>";
    public static final String NOTIFICATION_CHAT_FORMAT = "<dark_aqua>[Party] <sender>: <message></dark_aqua>";

    // ========================================
    // HELP SYSTEM
    // ========================================

    public static final List<String> HELP_COMMANDS = Arrays.asList(
            "<click:suggest_command:'/party help '><#2DA0ED>help [page]</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Shows this help menu</white>",
            "<click:suggest_command:'/party create'><#2DA0ED>create</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Creates a new party with you as leader</white>",
            "<click:suggest_command:'/party invite '><#2DA0ED>invite <player></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Sends a party invitation</white>",
            "<click:suggest_command:'/party uninvite '><#2DA0ED>uninvite <player></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Cancels a pending invitation</white>",
            "<click:suggest_command:'/party accept '><#2DA0ED>accept <leader></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Accepts a party invitation</white>",
            "<click:suggest_command:'/party reject '><#2DA0ED>reject <leader></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Rejects a party invitation</white>",
            "<click:suggest_command:'/party leave'><#2DA0ED>leave</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Leaves your current party</white>",
            "<click:suggest_command:'/party kick '><#2DA0ED>kick <member> [reason]</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Kicks a member</white>",
            "<click:suggest_command:'/party transfer '><#2DA0ED>transfer <member></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Transfers leadership</white>",
            "<click:suggest_command:'/party disband'><#2DA0ED>disband</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Disbands the party</white>",
            "<click:suggest_command:'/party list '><#2DA0ED>list [page]</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Lists party members</white>",
            "<click:suggest_command:'/party requests '><#2DA0ED>requests <direction> [page]</#2DA0ED></click> <dark_gray>—</dark_gray> <white>View party invitations</white>",
            "<click:suggest_command:'/party chat '><#2DA0ED>chat <message></#2DA0ED></click> <dark_gray>—</dark_gray> <white>Chat with party members</white>",
            "<click:suggest_command:'/party jump'><#2DA0ED>jump</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Warps you to the party leader</white>",
            "<click:suggest_command:'/party warp'><#2DA0ED>warp</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Warps all members to you</white>",
            "<click:suggest_command:'/party settings '><#2DA0ED>settings [<setting> <value>]</#2DA0ED></click> <dark_gray>—</dark_gray> <white>Manage party preferences</white>"
    );

    private PartyProxyConstants() {
    }
}
