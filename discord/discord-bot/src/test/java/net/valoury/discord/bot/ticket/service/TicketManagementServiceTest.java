package net.valoury.discord.bot.ticket.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadMemberPaginationAction;
import net.valoury.discord.api.ticket.AssignTicketResult;
import net.valoury.discord.api.ticket.BeginTicketCloseResult;
import net.valoury.discord.api.ticket.ReserveTicketResult;
import net.valoury.discord.api.ticket.Ticket;
import net.valoury.discord.api.ticket.TicketService;
import net.valoury.discord.api.ticket.TicketStatus;
import net.valoury.discord.api.ticket.TicketStorage;
import net.valoury.discord.bot.DiscordBotConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketManagementServiceTest {

    @Test
    void restoresTheThreadAndStorageWhenCloseCompletionFails() {
        FailingCloseTicketStorage storage = new FailingCloseTicketStorage();
        List<Boolean> lockedStates = new ArrayList<>();
        List<Boolean> archivedStates = new ArrayList<>();
        ThreadChannel thread = createThread(lockedStates, archivedStates);
        TicketManagementService service = new TicketManagementService(new TicketService(storage));

        String result = service.closeTicket(thread).join();

        assertEquals(DiscordBotConstants.TICKET_OPERATION_FAILED, result);
        assertTrue(storage.restoreRequested);
        assertEquals(List.of(true, false), lockedStates);
        assertEquals(List.of(false), archivedStates);
    }

    private static ThreadChannel createThread(
            List<Boolean> lockedStates,
            List<Boolean> archivedStates
    ) {
        ThreadChannelManager[] managerReference = new ThreadChannelManager[1];
        managerReference[0] = proxy(ThreadChannelManager.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "setLocked" -> {
                lockedStates.add((Boolean) arguments[0]);
                yield managerReference[0];
            }
            case "setArchived" -> {
                archivedStates.add((Boolean) arguments[0]);
                yield managerReference[0];
            }
            case "submit" -> CompletableFuture.completedFuture(null);
            default -> throw new UnsupportedOperationException(method.getName());
        });

        ThreadMemberPaginationAction membersAction = proxy(
                ThreadMemberPaginationAction.class,
                (proxy, method, arguments) -> {
                    if (method.getName().equals("submit")) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        SelfUser selfUser = proxy(SelfUser.class, (proxy, method, arguments) -> {
            if (method.getName().equals("getIdLong")) {
                return 999L;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        JDA jda = proxy(JDA.class, (proxy, method, arguments) -> {
            if (method.getName().equals("getSelfUser")) {
                return selfUser;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        return proxy(ThreadChannel.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getIdLong" -> 123L;
            case "getId" -> "123";
            case "getManager" -> managerReference[0];
            case "retrieveThreadMembers" -> membersAction;
            case "getJDA" -> jda;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> interfaceType, java.lang.reflect.InvocationHandler handler) {
        return interfaceType.cast(Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[]{interfaceType},
                handler
        ));
    }

    private static final class FailingCloseTicketStorage implements TicketStorage {
        private boolean restoreRequested;

        @Override
        public CompletableFuture<BeginTicketCloseResult> beginTicketClose(long threadId) {
            Ticket closingTicket = new Ticket(
                    1,
                    OptionalLong.of(threadId),
                    OptionalLong.of(456),
                    10,
                    20,
                    30,
                    TicketStatus.CLOSING,
                    Instant.EPOCH,
                    Optional.empty()
            );
            return CompletableFuture.completedFuture(new BeginTicketCloseResult.Ready(closingTicket));
        }

        @Override
        public CompletableFuture<Boolean> completeTicketClose(long threadId) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database unavailable"));
        }

        @Override
        public CompletableFuture<Boolean> restoreOpenTicket(long threadId) {
            restoreRequested = true;
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<ReserveTicketResult> reserveTicket(
                long ownerDiscordUserId,
                long guildId,
                long parentChannelId,
                long staffRoleId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Optional<Ticket>> activateTicket(long ticketNumber, long threadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> failTicketCreation(long ticketNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Optional<Ticket>> findOpenTicketByThread(long threadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AssignTicketResult> assignTicket(
                long threadId,
                long selectedDiscordUserId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
