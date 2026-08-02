package net.valoury.discord.bot;

public final class DiscordBotConstants {
    public static final String TOKEN = "FAKE_DISCORD_BOT_TOKEN";
    public static final String TICKET_COMMAND_NAME = "ticket";
    public static final String LINK_COMMAND_NAME = "link";
    public static final String UNLINK_COMMAND_NAME = "unlink";
    public static final String TICKET_OPEN_BUTTON_PREFIX = "ticket:open:";
    public static final String TICKET_THREAD_NAME_PREFIX = "TICKET・#";
    public static final String SUCCESS_FEEDBACK_PREFIX = "<a:valorV:1533039959651516498> - ";
    public static final String FAILURE_FEEDBACK_PREFIX = "<a:valorX:1533039960708350032> - ";

    public static final String TICKET_PANEL_TEXT = """
            ## !مسائل [فالوري](<https://discord.gg/invite/xkzC7meRdG>) ― نحن هنا دائماً لمساعدتك 🔍
            يجب عليك الإلتزام [بالقواعد العامة](<https://discord.com/channels/623179535008923684/1279208422595629120>) قبل إتباع هذه الإرشادات لكي تتمكن من إستخدام نظام المسائل.

            قدم لمحة عامة مختصرة عن مشكلتك لمساعدتنا في تقديم المساعدة بشكل أكثر فعالية. **:قدم وصفاً موجزاً**  **•**  📖_   _
            شارك جميع المعلومات والتفاصيل ذات الصلة بمشكلتك حتى نتمكن من حلها دون الحاجة إلى طلب معلومات إضافية منك. **:تضمين جميع التفاصيل اللازمة**  **•**  ✍️_   _

            **نحن نسعى للرد في غضون ساعة، ولكن يرجى السماح بما يصل إلى ٢٤ ساعة للرد.**
            """.strip();
    public static final String TICKET_OPEN_BUTTON_LABEL = "\u200B";
    public static final String TICKET_OPENING_TITLE = "🔑 A ticket has gotten open";
    public static final String TICKET_OPENING_REASON = "UNSPECIFIED";
    public static final String TICKET_OPENING_TEXT =
            "Thank you for your patience. A staff member will be with you shortly to assist. "
                    + "In the meantime, please provide as much detail as possible about your issue to help us "
                    + "understand and address your concern more efficiently.";
    public static final String TICKET_ALREADY_OPEN = FAILURE_FEEDBACK_PREFIX + "You already have an open ticket.";
    public static final String TICKET_OPENED = SUCCESS_FEEDBACK_PREFIX + "Your ticket has been opened.";
    public static final String TICKET_NOT_RECOGNIZED =
            FAILURE_FEEDBACK_PREFIX + "This thread is not a recognized open ticket.";
    public static final String TICKET_MISSING_OWNER =
            FAILURE_FEEDBACK_PREFIX + "This ticket does not have a recorded owner.";
    public static final String TICKET_ALREADY_CLOSING =
            FAILURE_FEEDBACK_PREFIX + "This ticket is already being closed.";
    public static final String TICKET_CLOSED = SUCCESS_FEEDBACK_PREFIX + "The ticket has been closed.";
    public static final String TICKET_CLOSED_ARCHIVE_FAILED =
            FAILURE_FEEDBACK_PREFIX + "The ticket was closed, but Discord did not archive the thread.";
    public static final String TICKET_ASSIGNED = SUCCESS_FEEDBACK_PREFIX + "The ticket owner has been updated.";
    public static final String TICKET_ALREADY_ASSIGNED =
            FAILURE_FEEDBACK_PREFIX + "The selected user already owns this ticket.";
    public static final String TICKET_SELECTED_USER_ALREADY_OWNS =
            FAILURE_FEEDBACK_PREFIX + "The selected user already owns another open ticket.";
    public static final String TICKET_USER_ADDED =
            SUCCESS_FEEDBACK_PREFIX + "The selected user has been added to the ticket.";
    public static final String TICKET_USER_ALREADY_ADDED =
            FAILURE_FEEDBACK_PREFIX + "The selected user is already in the ticket.";
    public static final String TICKET_USER_CANNOT_VIEW_PARENT_CHANNEL =
            FAILURE_FEEDBACK_PREFIX + "The selected user cannot view the ticket channel.";
    public static final String TICKET_USER_REMOVED =
            SUCCESS_FEEDBACK_PREFIX + "The selected user has been removed from the ticket.";
    public static final String TICKET_USER_NOT_PRESENT =
            FAILURE_FEEDBACK_PREFIX + "The selected user is not in the ticket.";
    public static final String TICKET_PROTECTED_USER =
            FAILURE_FEEDBACK_PREFIX + "That user cannot be removed from this ticket.";
    public static final String TICKET_PANEL_CREATED =
            SUCCESS_FEEDBACK_PREFIX + "The ticket panel has been created.";
    public static final String TICKET_PANEL_INVALID_CHANNEL =
            FAILURE_FEEDBACK_PREFIX + "Ticket panels must be created in a text channel.";
    public static final String TICKET_INVALID_STAFF_ROLE =
            FAILURE_FEEDBACK_PREFIX + "The staff role must be able to manage threads in this channel.";
    public static final String TICKET_MISSING_USER =
            FAILURE_FEEDBACK_PREFIX + "The selected user is no longer in this server.";
    public static final String TICKET_ADMINISTRATOR_ONLY =
            FAILURE_FEEDBACK_PREFIX + "Only administrators can manage tickets.";
    public static final String TICKET_INVALID_BUTTON =
            FAILURE_FEEDBACK_PREFIX + "This ticket button is no longer valid.";
    public static final String TICKET_MISSING_BOT_PERMISSIONS =
            FAILURE_FEEDBACK_PREFIX
                    + "The bot does not have the permissions required to manage private tickets here.";
    public static final String TICKET_OPERATION_FAILED =
            FAILURE_FEEDBACK_PREFIX + "The ticket operation could not be completed.";
    public static final String LINK_SUCCESS =
            SUCCESS_FEEDBACK_PREFIX + "Your Minecraft account is now linked.";
    public static final String LINK_ALREADY_LINKED =
            FAILURE_FEEDBACK_PREFIX + "These accounts are already linked.";
    public static final String LINK_MINECRAFT_ALREADY_LINKED =
            FAILURE_FEEDBACK_PREFIX + "That Minecraft account is already linked.";
    public static final String LINK_DISCORD_ALREADY_LINKED =
            FAILURE_FEEDBACK_PREFIX + "Your Discord account is already linked.";
    public static final String LINK_INVALID_OR_EXPIRED_CODE =
            FAILURE_FEEDBACK_PREFIX + "That code is invalid or expired. Create a new one in Minecraft.";
    public static final String LINK_RATE_LIMITED =
            FAILURE_FEEDBACK_PREFIX + "Too many attempts. Try again in %d seconds.";
    public static final String LINK_OPERATION_FAILED =
            FAILURE_FEEDBACK_PREFIX + "The account link operation could not be completed.";
    public static final String UNLINK_SUCCESS =
            SUCCESS_FEEDBACK_PREFIX + "Your Minecraft account has been unlinked.";
    public static final String UNLINK_NOT_LINKED =
            FAILURE_FEEDBACK_PREFIX + "Your Discord account is not linked.";

    private DiscordBotConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
