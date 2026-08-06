package net.valoury.discord.bot.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.valoury.discord.bot.DiscordBotConstants;
import net.valoury.discord.bot.link.command.LinkCommandFactory;
import net.valoury.discord.bot.evidence.command.EvidenceCommandFactory;
import net.valoury.discord.bot.ticket.command.TicketCommandFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class DiscordCommandRegistrar {
    private final TicketCommandFactory ticketCommandFactory;
    private final LinkCommandFactory linkCommandFactory;
    private final EvidenceCommandFactory evidenceCommandFactory;

    public DiscordCommandRegistrar() {
        this.ticketCommandFactory = new TicketCommandFactory();
        this.linkCommandFactory = new LinkCommandFactory();
        this.evidenceCommandFactory = new EvidenceCommandFactory();
    }

    public CompletableFuture<Void> register(JDA discordClient) {
        Objects.requireNonNull(discordClient, "Discord client cannot be null");
        Guild normalGuild = requireGuild(discordClient, DiscordBotConstants.NORMAL_GUILD_ID, "normal");
        Guild staffGuild = requireGuild(discordClient, DiscordBotConstants.STAFF_GUILD_ID, "staff");

        CompletableFuture<?> clearGlobalCommands = discordClient.updateCommands().submit();
        CompletableFuture<?> registerNormalGuildCommands = normalGuild.updateCommands()
                .addCommands(createSharedGuildCommands())
                .submit();
        CompletableFuture<?> registerStaffGuildCommands = staffGuild.updateCommands()
                .addCommands(createStaffGuildCommands())
                .submit();
        return CompletableFuture.allOf(
                clearGlobalCommands,
                registerNormalGuildCommands,
                registerStaffGuildCommands
        );
    }

    List<CommandData> createSharedGuildCommands() {
        return List.of(
                ticketCommandFactory.createTicketCommand(),
                linkCommandFactory.createLinkCommand(),
                linkCommandFactory.createUnlinkCommand()
        );
    }

    List<CommandData> createStaffGuildCommands() {
        return List.of(
                ticketCommandFactory.createTicketCommand(),
                linkCommandFactory.createLinkCommand(),
                linkCommandFactory.createUnlinkCommand(),
                evidenceCommandFactory.createEvidenceCommand()
        );
    }

    private static Guild requireGuild(JDA discordClient, long guildId, String guildDescription) {
        Guild guild = discordClient.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalStateException(
                    "Configured Discord " + guildDescription + " guild is unavailable: " + guildId
            );
        }
        return guild;
    }
}
