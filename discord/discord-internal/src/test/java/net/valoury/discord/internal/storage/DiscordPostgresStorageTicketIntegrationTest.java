package net.valoury.discord.internal.storage;

import net.valoury.discord.api.ticket.AssignTicketResult;
import net.valoury.discord.api.ticket.BeginTicketCloseResult;
import net.valoury.discord.api.ticket.ReserveTicketResult;
import net.valoury.discord.api.ticket.Ticket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("integrationTestsEnabled")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class DiscordPostgresStorageTicketIntegrationTest {
    private static final boolean ENABLED = false;
    private static final String POSTGRESQL_ADMIN_URL =
            "jdbc:postgresql://localhost:5432/postgres";
    private static final String POSTGRESQL_URL =
            "jdbc:postgresql://localhost:5432/discord_integration";
    private static final String POSTGRESQL_USER = "postgres";
    private static final String POSTGRESQL_PASSWORD = "postword";
    private DiscordPostgresStorage storage;
    private boolean databaseCreated;

    private static boolean integrationTestsEnabled() {
        return ENABLED;
    }

    @BeforeAll
    void initializeFreshTicketStorage() throws Exception {
        createDisposableDatabase();
        storage = new DiscordPostgresStorage(
                POSTGRESQL_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        );
    }

    @AfterAll
    void closeTicketStorage() throws Exception {
        if (storage != null) {
            storage.close();
        }
        if (databaseCreated) {
            dropDisposableDatabase();
        }
    }

    @Test
    void enforcesOwnershipAssignmentAndCloseInvariantsTransactionally() throws Exception {
        Ticket firstTicket = assertInstanceOf(
                ReserveTicketResult.Reserved.class,
                reserveTicket(101)
        ).ticket();
        ReserveTicketResult duplicateReservation = reserveTicket(101);
        assertInstanceOf(ReserveTicketResult.AlreadyOwnsOpenTicket.class, duplicateReservation);
        Ticket activeFirstTicket = storage.activateTicket(firstTicket.ticketNumber(), 1_001).join().orElseThrow();

        Ticket secondTicket = assertInstanceOf(
                ReserveTicketResult.Reserved.class,
                reserveTicket(202)
        ).ticket();
        storage.activateTicket(secondTicket.ticketNumber(), 1_002).join().orElseThrow();

        assertInstanceOf(
                AssignTicketResult.SelectedUserAlreadyOwnsOpenTicket.class,
                storage.assignTicket(activeFirstTicket.threadId().orElseThrow(), 202).join()
        );
        AssignTicketResult.Assigned assignment = assertInstanceOf(
                AssignTicketResult.Assigned.class,
                storage.assignTicket(activeFirstTicket.threadId().orElseThrow(), 303).join()
        );
        assertEquals(101, assignment.previousOwnerDiscordUserId());
        assertEquals(303, assignment.ticket().ownerDiscordUserId().orElseThrow());

        assertInstanceOf(ReserveTicketResult.Reserved.class, reserveTicket(101));
        assertInstanceOf(
                BeginTicketCloseResult.Ready.class,
                storage.beginTicketClose(activeFirstTicket.threadId().orElseThrow()).join()
        );
        assertTrue(storage.completeTicketClose(activeFirstTicket.threadId().orElseThrow()).join());
        assertInstanceOf(ReserveTicketResult.Reserved.class, reserveTicket(303));
        assertClosedOwnerRelationshipCleared(firstTicket.ticketNumber());

        CompletableFuture<ReserveTicketResult> concurrentReservationOne =
                storage.reserveTicket(404, 10, 20, 30);
        CompletableFuture<ReserveTicketResult> concurrentReservationTwo =
                storage.reserveTicket(404, 10, 20, 30);
        List<ReserveTicketResult> concurrentResults = List.of(
                concurrentReservationOne.join(),
                concurrentReservationTwo.join()
        );
        assertEquals(1, concurrentResults.stream()
                .filter(ReserveTicketResult.Reserved.class::isInstance)
                .count());
        assertEquals(1, concurrentResults.stream()
                .filter(ReserveTicketResult.AlreadyOwnsOpenTicket.class::isInstance)
                .count());
    }

    private ReserveTicketResult reserveTicket(long ownerDiscordUserId) {
        return storage.reserveTicket(ownerDiscordUserId, 10, 20, 30).join();
    }

    private void createDisposableDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL_ADMIN_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        ); PreparedStatement existenceStatement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'discord_integration')")) {
            try (ResultSet resultSet = existenceStatement.executeQuery()) {
                resultSet.next();
                if (resultSet.getBoolean(1)) {
                    throw new IllegalStateException(
                            "Refusing to use existing discord_integration database");
                }
            }
            try (Statement createStatement = connection.createStatement()) {
                createStatement.execute("""
                        CREATE DATABASE discord_integration
                        OWNER postgres
                        TEMPLATE template0
                        ENCODING 'UTF8'
                        """);
                databaseCreated = true;
            }
        }
    }

    private void dropDisposableDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL_ADMIN_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        ); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE discord_integration WITH (FORCE)");
            databaseCreated = false;
        }
    }

    private static void assertClosedOwnerRelationshipCleared(long ticketNumber) throws Exception {
        String sql = """
                SELECT status, owner_discord_user_id
                FROM discord_tickets
                WHERE ticket_number = ?
                """;
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        ); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ticketNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("CLOSED", resultSet.getString("status"));
                assertNull(resultSet.getObject("owner_discord_user_id"));
            }
        }
    }
}
