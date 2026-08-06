package net.valoury.discord.bot.evidence;

import net.valoury.discord.bot.DiscordBotConstants;

import java.time.Duration;

public final class EvidenceBotConstants {
    public static final String COMMAND_NAME = "evidence";
    public static final String SUBMIT_BUTTON_PREFIX = "evidence:submit:";
    public static final String EDIT_BUTTON_PREFIX = "evidence:edit:";
    public static final String EDIT_CHANGES_BUTTON_PREFIX = "evidence:edit-changes:";
    public static final String ACCEPT_BUTTON_PREFIX = "evidence:accept:";
    public static final String CHANGES_BUTTON_PREFIX = "evidence:changes:";
    public static final String SUBMISSION_MODAL_PREFIX = "evidence:submission:";
    public static final String EDIT_MODAL_PREFIX = "evidence:edit-modal:";
    public static final String EDIT_CHANGES_MODAL_PREFIX = "evidence:edit-changes-modal:";
    public static final String CHANGES_MODAL_PREFIX = "evidence:changes-modal:";

    public static final String INCIDENT_FIELD = "incident";
    public static final String PROOF_FIELD = "proof";
    public static final String CONTEXT_FIELD = "context";
    public static final String LINK_FIELD = "link";
    public static final String FILES_FIELD = "files";
    public static final String CHANGES_REASON_FIELD = "changes-reason";

    public static final long AWAITING_EVIDENCE_TAG_ID = 1534503720853831702L;
    public static final long AWAITING_REVIEW_TAG_ID = 1534503859702202406L;
    public static final long ACCEPTED_TAG_ID = 1534503955680333855L;
    public static final long NEEDS_CHANGES_TAG_ID = 1534504129655738408L;

    public static final int MAXIMUM_ATTACHMENTS = 10;
    public static final int FILE_COMPONENT_ID_BASE = 100;
    public static final long MAXIMUM_TOTAL_UPLOAD_BYTES = 100L * 1024L * 1024L;
    public static final Duration PROVISIONING_POLL_INTERVAL = Duration.ofSeconds(2);
    public static final Duration PROVISIONING_LEASE = Duration.ofMinutes(2);
    public static final Duration UPLOAD_LEASE = Duration.ofMinutes(10);
    public static final int PROVISIONING_BATCH_SIZE = 10;

    public static final String SETTINGS_SAVED =
            DiscordBotConstants.SUCCESS_FEEDBACK_PREFIX + "Evidence forum settings have been saved.";
    public static final String SETTINGS_INVALID_FORUM =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "Select a staff-only forum channel.";
    public static final String SETTINGS_INVALID_REVIEWER_ROLE =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "The reviewer role cannot access that forum.";
    public static final String SETTINGS_MISSING_REQUIRED_TAG =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX
                    + "That forum requires tags but has no `Awaiting Evidence` tag.";
    public static final String SETTINGS_MISSING_BOT_PERMISSIONS =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX
                    + "The bot lacks the permissions required to create and manage evidence posts.";
    public static final String ADMINISTRATOR_ONLY =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "Only administrators can configure evidence.";
    public static final String CASE_NOT_FOUND =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "This evidence case is no longer available.";
    public static final String CASE_WRONG_THREAD =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "Use the button inside this case's evidence post.";
    public static final String CASE_NOT_ASSIGNED =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "This evidence case is assigned to another staff member.";
    public static final String CASE_NOT_ACCEPTING_SUBMISSIONS =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "This evidence case is not accepting a submission.";
    public static final String CASE_ALREADY_SUBMITTED =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "Evidence was already submitted. Use the Edit button.";
    public static final String CASE_NOT_EDITABLE =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "This evidence submission can no longer be edited.";
    public static final String SUBMISSION_REQUIRES_PROOF =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "Upload at least one file or provide an HTTPS link.";
    public static final String SUBMISSION_INVALID_LINK =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "The external evidence link must be a valid HTTPS URL.";
    public static final String SUBMISSION_INVALID_FILE =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX
                    + "Evidence files must be supported images, videos, or plain-text logs.";
    public static final String SUBMISSION_TOO_LARGE =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "One or more evidence files exceed the upload limit.";
    public static final String SUBMISSION_COMPLETE =
            DiscordBotConstants.SUCCESS_FEEDBACK_PREFIX + "Your evidence has been submitted for review.";
    public static final String EDIT_COMPLETE =
            DiscordBotConstants.SUCCESS_FEEDBACK_PREFIX + "Your evidence changes have been saved for review.";
    public static final String EDIT_REQUIRES_CHANGES =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "Change at least one field or upload replacement files.";
    public static final String EDIT_REQUIRES_ATTACHMENT_REUPLOAD =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX
                    + "Re-upload all previously attached evidence proof files in this edit. "
                    + "Replacement uploads replace the entire previous attachment set.";
    public static final String REVIEWER_ONLY =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "Only evidence reviewers can perform this action.";
    public static final String REVIEW_NO_LONGER_PENDING =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "This evidence submission is no longer awaiting review.";
    public static final String REVIEW_ACCEPTED =
            DiscordBotConstants.SUCCESS_FEEDBACK_PREFIX + "The evidence has been accepted and archived.";
    public static final String REVIEW_CHANGES_REQUESTED =
            DiscordBotConstants.SUCCESS_FEEDBACK_PREFIX + "The requested changes have been sent to the issuer.";
    public static final String REVIEW_CHANGE_REQUEST_EDITED =
            DiscordBotConstants.SUCCESS_FEEDBACK_PREFIX + "The change request has been updated.";
    public static final String OPERATION_FAILED =
            DiscordBotConstants.FAILURE_FEEDBACK_PREFIX + "The evidence operation could not be completed.";

    private EvidenceBotConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
