package com.zornus.parties.proxy;

public final class PartyProxyConstants {

    public static final String POSTGRESQL_URL = System.getenv("PARTY_POSTGRESQL_URL");
    public static final String POSTGRESQL_USER = System.getenv("PARTY_POSTGRESQL_USER");
    public static final String POSTGRESQL_PASSWORD = System.getenv("PARTY_POSTGRESQL_PASSWORD");

    public static final int DATABASE_CONNECTION_POOL_SIZE = 10;
    public static final int DATABASE_EXECUTOR_POOL_SIZE = 10;
    public static final long DATABASE_SHUTDOWN_TIMEOUT_SECONDS = 5;

    public static final int MAX_PARTY_SIZE = 8;
    public static final int MAX_PARTY_INVITATIONS = 20;

    public static final java.time.Duration INVITATION_COOLDOWN = java.time.Duration.ofMinutes(1);
    public static final java.time.Duration CONFIRMATION_EXPIRY = java.time.Duration.ofMinutes(1);
    public static final java.time.Duration INVITATION_EXPIRY = java.time.Duration.ofMinutes(5);
    public static final java.time.Duration WARP_COOLDOWN = java.time.Duration.ofSeconds(30);

    public static final int MAX_MESSAGE_LENGTH = 256;

    public static final String NOTIFICATION_MEMBER_JOINED = "<green><sender> joined the party.";
    public static final String NOTIFICATION_MEMBER_LEFT = "<yellow><sender> left the party.";
    public static final String NOTIFICATION_MEMBER_KICKED = "<red><member> was kicked from the party.";
    public static final String NOTIFICATION_MEMBER_KICKED_WITH_REASON = "<red><member> was kicked from the party. Reason: <reason>";
    public static final String NOTIFICATION_MEMBER_DISCONNECTED = "<yellow><player> disconnected from the party.";
    public static final String NOTIFICATION_LEADER_DISCONNECTED = "<yellow><old_leader> disconnected. <new_leader> is now the party leader.";
    public static final String NOTIFICATION_LEADERSHIP_TRANSFERRED = "<yellow><sender> transferred leadership to <member>.";
    public static final String NOTIFICATION_INVITE_RECEIVED = "<green><player> invited you to join their party '<party>'.";
    public static final String NOTIFICATION_INVITE_SENT_ANNOUNCEMENT = "<green><sender> invited <target> to the party.";
    public static final String NOTIFICATION_MEMBER_WARPED = "<green>You were warped to <sender>'s location.";
    public static final String NOTIFICATION_PARTY_DISBANDED = "<red>The party has been disbanded by <leader>.";
    public static final String NOTIFICATION_CHAT_FORMAT = "<dark_aqua>[Party] <sender>: <message>";

    private PartyProxyConstants() {
    }
}
