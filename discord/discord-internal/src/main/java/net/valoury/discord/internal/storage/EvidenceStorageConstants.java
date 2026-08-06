package net.valoury.discord.internal.storage;

final class EvidenceStorageConstants {
    static final String CASE_COLUMNS = """
            case_id, punishment_identifier, punished_player_id, punished_player_name,
            issuing_player_id, issuing_discord_user_id, preset_name, preset_application_number,
            punishment_type, reason, punishment_created_at, punishment_expires_at, status,
            guild_id, forum_channel_id, thread_id, starter_message_id, created_at
            """;

    static final String INITIAL_SUBMISSION_STATUS_SQL = "('AWAITING_EVIDENCE')";
    static final String EDITABLE_SUBMISSION_STATUS_SQL = "('AWAITING_REVIEW', 'NEEDS_CHANGES')";

    private EvidenceStorageConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
