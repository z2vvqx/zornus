package net.valoury.discord.proxy;

import java.util.List;

public final class DiscordProxyConstants {
    public static final String LINK_CODE_ISSUED = "<green>Click <discord_command> to paste it into chat, then copy and send it in Discord within five minutes.</green>";
    public static final String LINK_ALREADY_LINKED = "<red>Your Minecraft account is already linked. Use /unlink first.</red>";
    public static final String LINK_RATE_LIMITED = "<red>Please wait <seconds> seconds before creating another link code.</red>";
    public static final String UNLINK_SUCCESS = "<green>Your Discord account has been unlinked.</green>";
    public static final String UNLINK_NOT_LINKED = "<red>Your Minecraft account is not linked.</red>";
    public static final String UI_HELP_PAGINATION = "<gray>Page <current_page>/<maximum_pages> - /discord help <page></gray>";
    public static final List<String> HELP_COMMANDS = List.of(
            "<click:suggest_command:'/discord help '><#2DA0ED>help [page]</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Shows this help menu</white>",
            "<click:run_command:'/discord link'><#2DA0ED>link</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Creates a Discord link code</white>",
            "<click:run_command:'/discord unlink'><#2DA0ED>unlink</#2DA0ED></click> <dark_gray>─</dark_gray> <white>Unlinks your Discord account</white>"
    );

    private DiscordProxyConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
