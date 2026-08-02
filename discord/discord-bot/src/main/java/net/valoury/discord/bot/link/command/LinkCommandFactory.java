package net.valoury.discord.bot.link.command;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.valoury.discord.bot.DiscordBotConstants;

public final class LinkCommandFactory {
    public SlashCommandData createLinkCommand() {
        OptionData codeOption = new OptionData(
                OptionType.STRING,
                "code",
                "The one-time code shown by /link in Minecraft",
                true
        ).setMinLength(12).setMaxLength(14);
        return Commands.slash(
                        DiscordBotConstants.LINK_COMMAND_NAME,
                        "Link your Minecraft account"
                )
                .setContexts(InteractionContextType.GUILD, InteractionContextType.BOT_DM)
                .addOptions(codeOption);
    }

    public SlashCommandData createUnlinkCommand() {
        return Commands.slash(
                        DiscordBotConstants.UNLINK_COMMAND_NAME,
                        "Unlink your Minecraft account"
                )
                .setContexts(InteractionContextType.GUILD, InteractionContextType.BOT_DM);
    }
}
