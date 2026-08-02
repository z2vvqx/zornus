package net.valoury.discord.bot.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.valoury.discord.bot.link.command.LinkCommandFactory;
import net.valoury.discord.bot.ticket.command.TicketCommandFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class DiscordCommandRegistrar {
    private final TicketCommandFactory ticketCommandFactory;
    private final LinkCommandFactory linkCommandFactory;

    public DiscordCommandRegistrar() {
        this.ticketCommandFactory = new TicketCommandFactory();
        this.linkCommandFactory = new LinkCommandFactory();
    }

    public CompletableFuture<Void> register(JDA discordClient) {
        Objects.requireNonNull(discordClient, "Discord client cannot be null");
        return discordClient.updateCommands()
                .addCommands(createCommands())
                .submit()
                .thenApply(ignored -> null);
    }

    List<CommandData> createCommands() {
        return List.of(
                ticketCommandFactory.createTicketCommand(),
                linkCommandFactory.createLinkCommand(),
                linkCommandFactory.createUnlinkCommand()
        );
    }
}
