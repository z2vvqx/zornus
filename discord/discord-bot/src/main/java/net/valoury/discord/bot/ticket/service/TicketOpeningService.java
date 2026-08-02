package net.valoury.discord.bot.ticket.service;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.valoury.discord.api.ticket.ReserveTicketResult;
import net.valoury.discord.api.ticket.Ticket;
import net.valoury.discord.api.ticket.TicketService;
import net.valoury.discord.bot.DiscordBotConstants;
import net.valoury.discord.bot.ticket.TicketThreadName;
import net.valoury.discord.bot.ticket.message.TicketMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static net.valoury.discord.bot.async.CompletionExceptionUnwrapper.unwrap;

public final class TicketOpeningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketOpeningService.class);

    private final TicketService ticketService;
    private final TicketMessageFactory ticketMessageFactory;

    public TicketOpeningService(TicketService ticketService, TicketMessageFactory ticketMessageFactory) {
        this.ticketService = Objects.requireNonNull(ticketService, "Ticket service cannot be null");
        this.ticketMessageFactory = Objects.requireNonNull(
                ticketMessageFactory,
                "Ticket message factory cannot be null"
        );
    }

    public CompletableFuture<String> createTicketPanel(TextChannel channel, Role staffRole) {
        if (!staffRoleCanManageTickets(staffRole, channel)) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_INVALID_STAFF_ROLE);
        }
        if (!botCanManageTickets(channel)) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_MISSING_BOT_PERMISSIONS);
        }

        return channel.sendMessage(ticketMessageFactory.ticketPanel(staffRole.getIdLong()))
                .setAllowedMentions(Collections.emptySet())
                .submit()
                .thenApply(ignored -> DiscordBotConstants.TICKET_PANEL_CREATED)
                .exceptionally(exception -> operationFailed(
                        "create ticket panel in channel " + channel.getId(), exception));
    }

    public CompletableFuture<String> openTicket(TextChannel parentChannel, User owner, Role staffRole) {
        if (!staffRoleCanManageTickets(staffRole, parentChannel)) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_INVALID_BUTTON);
        }
        if (!botCanManageTickets(parentChannel)) {
            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_MISSING_BOT_PERMISSIONS);
        }

        return ticketService.reserveTicket(
                        owner.getIdLong(),
                        parentChannel.getGuild().getIdLong(),
                        parentChannel.getIdLong(),
                        staffRole.getIdLong())
                .thenCompose(result -> switch (result) {
                    case ReserveTicketResult.AlreadyOwnsOpenTicket ignored ->
                            CompletableFuture.completedFuture(DiscordBotConstants.TICKET_ALREADY_OPEN);
                    case ReserveTicketResult.Reserved reserved ->
                            createReservedTicket(parentChannel, owner, staffRole, reserved.ticket());
                })
                .exceptionally(exception -> operationFailed(
                        "open ticket for Discord user " + owner.getId(), exception));
    }

    private CompletableFuture<String> createReservedTicket(
            TextChannel parentChannel,
            User owner,
            Role staffRole,
            Ticket reservedTicket
    ) {
        return parentChannel.createThreadChannel(
                        TicketThreadName.format(reservedTicket.ticketNumber()),
                        true)
                .setInvitable(false)
                .submit()
                .exceptionallyCompose(exception -> failReservation(reservedTicket, exception))
                .thenCompose(thread -> activateAndPopulateTicket(thread, owner, staffRole, reservedTicket)
                        .exceptionallyCompose(exception -> abortCreatedTicket(
                                thread, reservedTicket, exception)));
    }

    private CompletableFuture<String> activateAndPopulateTicket(
            ThreadChannel thread,
            User owner,
            Role staffRole,
            Ticket reservedTicket
    ) {
        return ticketService.activateTicket(reservedTicket.ticketNumber(), thread.getIdLong())
                .thenCompose(activeTicket -> {
                    if (activeTicket.isEmpty()) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Ticket reservation was no longer active"));
                    }

                    Container openingMessage = ticketMessageFactory.ticketOpening(
                            staffRole.getIdLong(),
                            owner.getIdLong());
                    return thread.sendMessageComponents(openingMessage)
                            .useComponentsV2()
                            .setAllowedMentions(EnumSet.of(Message.MentionType.ROLE))
                            .mentionRoles(staffRole.getIdLong())
                            .submit()
                            .thenCompose(ignored -> thread.addThreadMemberById(owner.getIdLong()).submit())
                            .thenApply(ignored -> DiscordBotConstants.TICKET_OPENED);
                });
    }

    private <T> CompletableFuture<T> failReservation(Ticket reservedTicket, Throwable originalException) {
        return ticketService.failTicketCreation(reservedTicket.ticketNumber())
                .handle((ignored, compensationException) -> {
                    if (compensationException != null) {
                        originalException.addSuppressed(unwrap(compensationException));
                    }
                    throw new CompletionException(unwrap(originalException));
                });
    }

    private CompletableFuture<String> abortCreatedTicket(
            ThreadChannel thread,
            Ticket reservedTicket,
            Throwable originalException
    ) {
        CompletableFuture<Void> releaseReservation = ticketService
                .failTicketCreation(reservedTicket.ticketNumber())
                .exceptionally(exception -> {
                    originalException.addSuppressed(unwrap(exception));
                    return null;
                });
        CompletableFuture<Void> archiveThread = thread.getManager()
                .setLocked(true)
                .setArchived(true)
                .submit()
                .exceptionally(exception -> {
                    originalException.addSuppressed(unwrap(exception));
                    return null;
                });
        return CompletableFuture.allOf(releaseReservation, archiveThread)
                .thenCompose(ignored -> CompletableFuture.failedFuture(unwrap(originalException)));
    }

    private static boolean staffRoleCanManageTickets(Role staffRole, TextChannel channel) {
        return staffRole.getGuild().getIdLong() == channel.getGuild().getIdLong()
                && (staffRole.hasPermission(Permission.ADMINISTRATOR)
                || staffRole.hasPermission(channel, Permission.MANAGE_THREADS));
    }

    private static boolean botCanManageTickets(TextChannel channel) {
        Member botMember = channel.getGuild().getSelfMember();
        return botMember.hasPermission(channel,
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.CREATE_PRIVATE_THREADS,
                Permission.MANAGE_THREADS,
                Permission.MESSAGE_SEND_IN_THREADS);
    }

    private static String operationFailed(String operation, Throwable exception) {
        LOGGER.error("Failed to {}", operation, unwrap(exception));
        return DiscordBotConstants.TICKET_OPERATION_FAILED;
    }

}
