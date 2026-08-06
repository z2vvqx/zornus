package net.valoury.discord.bot.evidence.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.valoury.discord.bot.evidence.EvidenceBotConstants;

public final class EvidenceCommandFactory {
    public SlashCommandData createEvidenceCommand() {
        OptionData forumOption = new OptionData(
                OptionType.CHANNEL,
                "forum",
                "The staff-only forum used for punishment evidence",
                true
        ).setChannelTypes(ChannelType.FORUM);
        OptionData reviewerRoleOption = new OptionData(
                OptionType.ROLE,
                "reviewer-role",
                "The role allowed to accept evidence or request changes",
                true
        );
        return Commands.slash(EvidenceBotConstants.COMMAND_NAME, "Configure punishment evidence")
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                        new SubcommandData("setup", "Configure the evidence forum and reviewer role")
                                .addOptions(forumOption, reviewerRoleOption)
                );
    }
}
