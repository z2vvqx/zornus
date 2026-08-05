package net.valoury.discord.bot.ticket.service;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.ThreadMember;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.valoury.discord.api.ticket.AssignTicketResult;
import net.valoury.discord.api.ticket.BeginTicketCloseResult;
import net.valoury.discord.api.ticket.Ticket;
import net.valoury.discord.api.ticket.TicketService;
import net.valoury.discord.bot.DiscordBotConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static net.valoury.discord.bot.async.CompletionExceptionUnwrapper.unwrap;

public final class TicketManagementService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketManagementService.class);

    private final TicketService ticketService;

    public TicketManagementService(TicketService ticketService) {
        this.ticketService = Objects.requireNonNull(ticketService, "Ticket service cannot be null");
    }

    public CompletableFuture<String> closeTicket(ThreadChannel thread) {
        return ticketService.beginTicketClose(thread.getIdLong())
                .thenCompose(result -> switch (result) {
                    case BeginTicketCloseResult.TicketNotFound ignored ->
                            CompletableFuture.completedFuture(DiscordBotConstants.TICKET_NOT_RECOGNIZED);
                    case BeginTicketCloseResult.MissingOwner ignored ->
                            CompletableFuture.completedFuture(DiscordBotConstants.TICKET_MISSING_OWNER);
                    case BeginTicketCloseResult.AlreadyClosing ignored ->
                            CompletableFuture.completedFuture(DiscordBotConstants.TICKET_ALREADY_CLOSING);
                    case BeginTicketCloseResult.Ready ignored -> closePreparedTicket(thread);
                })
                .exceptionally(exception -> operationFailed(
                        "close ticket thread " + thread.getId(), exception));
    }

    public CompletableFuture<String> assignTicket(ThreadChannel thread, Member selectedMember) {
        return ticketService.assignTicket(thread.getIdLong(), selectedMember.getIdLong())
                .thenApply(result -> switch (result) {
                    case AssignTicketResult.Assigned ignored -> DiscordBotConstants.TICKET_ASSIGNED;
                    case AssignTicketResult.TicketNotFound ignored -> DiscordBotConstants.TICKET_NOT_RECOGNIZED;
                    case AssignTicketResult.MissingOwner ignored -> DiscordBotConstants.TICKET_MISSING_OWNER;
                    case AssignTicketResult.AlreadyOwner ignored -> DiscordBotConstants.TICKET_ALREADY_ASSIGNED;
                    case AssignTicketResult.SelectedUserAlreadyOwnsOpenTicket ignored ->
                            DiscordBotConstants.TICKET_SELECTED_USER_ALREADY_OWNS;
                })
                .exceptionally(exception -> operationFailed(
                        "assign ticket thread " + thread.getId()
                                + " to Discord user " + selectedMember.getId(), exception));
    }

    public CompletableFuture<String> addUser(ThreadChannel thread, Member selectedMember) {
        return ticketService.findOpenTicketByThread(thread.getIdLong())
                .thenCompose(ticketOptional -> {
                    if (ticketOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_NOT_RECOGNIZED);
                    }
                    Ticket ticket = ticketOptional.orElseThrow();
                    if (ticket.ownerDiscordUserId().isEmpty()) {
                        return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_MISSING_OWNER);
                    }
                    if (!selectedMember.hasPermission(thread.getParentChannel(), Permission.VIEW_CHANNEL)) {
                        return CompletableFuture.completedFuture(
                                DiscordBotConstants.TICKET_USER_CANNOT_VIEW_PARENT_CHANNEL);
                    }
                    return thread.retrieveThreadMembers().submit().thenCompose(threadMembers -> {
                        boolean alreadyPresent = threadMembers.stream()
                                .anyMatch(threadMember -> threadMember.getIdLong() == selectedMember.getIdLong());
                        if (alreadyPresent) {
                            return CompletableFuture.completedFuture(
                                    DiscordBotConstants.TICKET_USER_ALREADY_ADDED);
                        }
                        return thread.addThreadMemberById(selectedMember.getIdLong())
                                .submit()
                                .thenApply(ignored -> DiscordBotConstants.TICKET_USER_ADDED);
                    });
                })
                .exceptionally(exception -> operationFailed(
                        "add Discord user " + selectedMember.getId()
                                + " to ticket thread " + thread.getId(), exception));
    }

    public CompletableFuture<String> removeUser(ThreadChannel thread, Member selectedMember) {
        return ticketService.findOpenTicketByThread(thread.getIdLong())
                .thenCompose(ticketOptional -> {
                    if (ticketOptional.isEmpty()) {
                        return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_NOT_RECOGNIZED);
                    }
                    Ticket ticket = ticketOptional.orElseThrow();
                    if (ticket.ownerDiscordUserId().isEmpty()) {
                        return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_MISSING_OWNER);
                    }
                    if (isProtectedParticipant(thread, ticket, selectedMember)) {
                        return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_PROTECTED_USER);
                    }
                    return thread.retrieveThreadMembers().submit().thenCompose(threadMembers -> {
                        boolean present = threadMembers.stream()
                                .anyMatch(threadMember -> threadMember.getIdLong() == selectedMember.getIdLong());
                        if (!present) {
                            return CompletableFuture.completedFuture(DiscordBotConstants.TICKET_USER_NOT_PRESENT);
                        }
                        return thread.removeThreadMemberById(selectedMember.getIdLong())
                                .submit()
                                .thenApply(ignored -> DiscordBotConstants.TICKET_USER_REMOVED);
                    });
                })
                .exceptionally(exception -> operationFailed(
                        "remove Discord user " + selectedMember.getId()
                                + " from ticket thread " + thread.getId(), exception));
    }

    private CompletableFuture<String> closePreparedTicket(ThreadChannel thread) {
        return prepareThreadForClose(thread)
                .thenCompose(removedParticipantIds -> ticketService.completeTicketClose(thread.getIdLong())
                        .thenCompose(completed -> {
                            if (!completed) {
                                IllegalStateException exception = new IllegalStateException(
                                        "Ticket closure state changed before completion");
                                return compensateFailedClose(thread, removedParticipantIds, exception)
                                        .thenApply(ignored -> DiscordBotConstants.TICKET_OPERATION_FAILED);
                            }
                            return thread.getManager().setArchived(true).submit()
                                    .handle((ignored, archiveException) -> {
                                        if (archiveException != null) {
                                            LOGGER.error(
                                                    "Discord ticket {} closed but could not be archived",
                                                    thread.getId(),
                                                    unwrap(archiveException));
                                            return DiscordBotConstants.TICKET_CLOSED_ARCHIVE_FAILED;
                                        }
                                        return DiscordBotConstants.TICKET_CLOSED;
                                    });
                        })
                        .exceptionallyCompose(exception -> {
                            Throwable cause = unwrap(exception);
                            return compensateFailedClose(thread, removedParticipantIds, cause)
                                    .thenCompose(ignored -> CompletableFuture.failedFuture(cause));
                        }))
                .exceptionally(exception -> operationFailed(
                        "close ticket thread " + thread.getId(), exception));
    }

    private CompletableFuture<List<Long>> prepareThreadForClose(ThreadChannel thread) {
        return thread.getManager().setLocked(true).submit()
                .thenCompose(ignored -> thread.retrieveThreadMembers().submit())
                .thenCompose(threadMembers -> {
                    long botUserId = thread.getJDA().getSelfUser().getIdLong();
                    List<Long> removableParticipantIds = threadMembers.stream()
                            .map(ThreadMember::getIdLong)
                            .filter(participantId -> participantId != botUserId)
                            .toList();
                    return removeParticipantsSequentially(thread, removableParticipantIds);
                })
                .exceptionallyCompose(exception -> {
                    Throwable cause = unwrap(exception);
                    List<Long> removedParticipantIds = cause instanceof ParticipantRemovalException removalException
                            ? removalException.removedParticipantIds()
                            : List.of();
                    return compensateFailedClose(thread, removedParticipantIds, cause)
                            .thenCompose(ignored -> CompletableFuture.failedFuture(cause));
                });
    }

    private CompletableFuture<List<Long>> removeParticipantsSequentially(
            ThreadChannel thread,
            List<Long> participantIds
    ) {
        CompletableFuture<List<Long>> progress = CompletableFuture.completedFuture(List.of());
        for (long participantId : participantIds) {
            progress = progress.thenCompose(removedParticipantIds ->
                    thread.removeThreadMemberById(participantId).submit()
                            .thenApply(ignored -> {
                                List<Long> updatedParticipantIds = new ArrayList<>(removedParticipantIds);
                                updatedParticipantIds.add(participantId);
                                return List.copyOf(updatedParticipantIds);
                            })
                            .exceptionallyCompose(exception -> CompletableFuture.failedFuture(
                                    new ParticipantRemovalException(removedParticipantIds, unwrap(exception)))));
        }
        return progress;
    }

    private CompletableFuture<Void> compensateFailedClose(
            ThreadChannel thread,
            List<Long> removedParticipantIds,
            Throwable originalException
    ) {
        CompletableFuture<Void> restoreStorage = ticketService.restoreOpenTicket(thread.getIdLong())
                .thenCompose(restored -> restored
                        ? CompletableFuture.<Void>completedFuture(null)
                        : CompletableFuture.<Void>failedFuture(new IllegalStateException(
                                "Ticket storage did not restore the closing ticket")))
                .exceptionally(exception -> {
                    originalException.addSuppressed(unwrap(exception));
                    return null;
                });
        CompletableFuture<Void> restoreThread = thread.getManager()
                .setLocked(false)
                .setArchived(false)
                .submit()
                .thenCompose(ignored -> restoreParticipants(thread, removedParticipantIds))
                .exceptionally(exception -> {
                    originalException.addSuppressed(unwrap(exception));
                    return null;
                });
        return CompletableFuture.allOf(restoreStorage, restoreThread);
    }

    private static CompletableFuture<Void> restoreParticipants(
            ThreadChannel thread,
            List<Long> removedParticipantIds
    ) {
        CompletableFuture<?>[] additions = removedParticipantIds.stream()
                .map(thread::addThreadMemberById)
                .map(action -> action.submit())
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(additions);
    }

    private static boolean isProtectedParticipant(ThreadChannel thread, Ticket ticket, Member member) {
        if (member.getIdLong() == thread.getJDA().getSelfUser().getIdLong()) {
            return true;
        }
        if (ticket.ownerDiscordUserId().isPresent()
                && ticket.ownerDiscordUserId().getAsLong() == member.getIdLong()) {
            return true;
        }
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        return member.getRoles().stream()
                .anyMatch(role -> role.getIdLong() == ticket.staffRoleId());
    }

    private static String operationFailed(String operation, Throwable exception) {
        LOGGER.error("Failed to {}", operation, unwrap(exception));
        return DiscordBotConstants.TICKET_OPERATION_FAILED;
    }

    private static final class ParticipantRemovalException extends RuntimeException {
        private final List<Long> removedParticipantIds;

        private ParticipantRemovalException(List<Long> removedParticipantIds, Throwable cause) {
            super("Failed to remove every ticket participant", cause);
            this.removedParticipantIds = List.copyOf(removedParticipantIds);
        }

        private List<Long> removedParticipantIds() {
            return removedParticipantIds;
        }
    }
}
