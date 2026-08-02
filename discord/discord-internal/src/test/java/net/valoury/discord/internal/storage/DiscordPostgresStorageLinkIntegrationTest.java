package net.valoury.discord.internal.storage;

import net.valoury.discord.api.link.AccountLink;
import net.valoury.discord.api.link.AccountLinkService;
import net.valoury.discord.api.link.ConsumeLinkCodeResult;
import net.valoury.discord.api.link.IssueLinkCodeResult;
import net.valoury.discord.api.link.UnlinkAccountResult;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("integrationTestsEnabled")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class DiscordPostgresStorageLinkIntegrationTest {
    private static final boolean ENABLED = false;
    private static final String DATABASE_NAME = "discord_link_integration";
    private static final String POSTGRESQL_ADMIN_URL =
            "jdbc:postgresql://localhost:5432/postgres";
    private static final String POSTGRESQL_URL =
            "jdbc:postgresql://localhost:5432/" + DATABASE_NAME;
    private static final String POSTGRESQL_USER = "postgres";
    private static final String POSTGRESQL_PASSWORD = "postword";
    private static final UUID FIRST_MINECRAFT_UNIQUE_ID =
            UUID.fromString("dd37c205-c8cc-4e12-82a1-e951c82ea9c6");
    private static final UUID SECOND_MINECRAFT_UNIQUE_ID =
            UUID.fromString("0919de3f-f92e-480f-a6d8-1e1dd96f04d8");
    private static final UUID CONCURRENT_MINECRAFT_UNIQUE_ID =
            UUID.fromString("f00bc34f-e06a-47b1-8115-a4e970a02b70");

    private DiscordPostgresStorage storage;
    private AccountLinkService accountLinkService;
    private boolean databaseCreated;

    private static boolean integrationTestsEnabled() {
        return ENABLED;
    }

    @BeforeAll
    void initializeFreshLinkStorage() throws Exception {
        createDisposableDatabase();
        storage = new DiscordPostgresStorage(
                POSTGRESQL_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        );
        accountLinkService = new AccountLinkService(storage);
    }

    @AfterAll
    void closeLinkStorage() throws Exception {
        if (storage != null) {
            storage.close();
        }
        if (databaseCreated) {
            dropDisposableDatabase();
        }
    }

    @Test
    void enforcesHashedOneTimeCodesUniqueIdentitiesLimitsAndUnlinking() throws Exception {
        IssueLinkCodeResult.Issued firstCode = issueCode(
                FIRST_MINECRAFT_UNIQUE_ID, "FirstPlayer");
        assertStoredCodeIsHashed(FIRST_MINECRAFT_UNIQUE_ID, firstCode.code());
        assertInstanceOf(
                IssueLinkCodeResult.RateLimited.class,
                accountLinkService.issueLinkCode(FIRST_MINECRAFT_UNIQUE_ID, "FirstPlayer").join()
        );

        for (int attempt = 0; attempt < 5; attempt++) {
            assertInstanceOf(
                    ConsumeLinkCodeResult.InvalidOrExpiredCode.class,
                    accountLinkService.consumeLinkCode(101, "AAAA-AAAA-AAAB").join()
            );
        }
        assertInstanceOf(
                ConsumeLinkCodeResult.RateLimited.class,
                accountLinkService.consumeLinkCode(101, "AAAA-AAAA-AAAC").join()
        );

        ConsumeLinkCodeResult.Linked firstLink = assertInstanceOf(
                ConsumeLinkCodeResult.Linked.class,
                accountLinkService.consumeLinkCode(201, firstCode.code()).join()
        );
        assertEquals(FIRST_MINECRAFT_UNIQUE_ID, firstLink.accountLink().minecraftUniqueId());
        assertEquals(
                firstLink.accountLink(),
                accountLinkService.findByMinecraftUniqueId(FIRST_MINECRAFT_UNIQUE_ID).join().orElseThrow()
        );
        assertEquals(
                firstLink.accountLink(),
                accountLinkService.findByDiscordUserId(201).join().orElseThrow()
        );
        assertInstanceOf(
                ConsumeLinkCodeResult.InvalidOrExpiredCode.class,
                accountLinkService.consumeLinkCode(202, firstCode.code()).join()
        );
        assertInstanceOf(
                IssueLinkCodeResult.AlreadyLinked.class,
                accountLinkService.issueLinkCode(FIRST_MINECRAFT_UNIQUE_ID, "FirstPlayer").join()
        );

        IssueLinkCodeResult.Issued secondCode = issueCode(
                SECOND_MINECRAFT_UNIQUE_ID, "SecondPlayer");
        assertInstanceOf(
                ConsumeLinkCodeResult.DiscordAccountLinkedElsewhere.class,
                accountLinkService.consumeLinkCode(201, secondCode.code()).join()
        );
        assertInstanceOf(
                UnlinkAccountResult.Unlinked.class,
                accountLinkService.unlinkByDiscordUserId(201).join()
        );
        assertInstanceOf(
                ConsumeLinkCodeResult.Linked.class,
                accountLinkService.consumeLinkCode(201, secondCode.code()).join()
        );
        assertInstanceOf(
                UnlinkAccountResult.Unlinked.class,
                accountLinkService.unlinkByMinecraftUniqueId(SECOND_MINECRAFT_UNIQUE_ID).join()
        );
        assertTrue(accountLinkService.findByDiscordUserId(201).join().isEmpty());

        IssueLinkCodeResult.Issued concurrentCode = issueCode(
                CONCURRENT_MINECRAFT_UNIQUE_ID, "RacePlayer");
        List<CompletableFuture<ConsumeLinkCodeResult>> concurrentConsumptions = List.of(
                accountLinkService.consumeLinkCode(301, concurrentCode.code()),
                accountLinkService.consumeLinkCode(302, concurrentCode.code())
        );
        CompletableFuture.allOf(concurrentConsumptions.toArray(CompletableFuture[]::new)).join();
        List<ConsumeLinkCodeResult> concurrentResults = concurrentConsumptions.stream()
                .map(CompletableFuture::join)
                .toList();
        assertEquals(1, concurrentResults.stream()
                .filter(ConsumeLinkCodeResult.Linked.class::isInstance)
                .count());
        Optional<AccountLink> concurrentLink = accountLinkService
                .findByMinecraftUniqueId(CONCURRENT_MINECRAFT_UNIQUE_ID)
                .join();
        assertTrue(concurrentLink.isPresent());
        assertTrue(concurrentLink.orElseThrow().discordUserId() == 301
                || concurrentLink.orElseThrow().discordUserId() == 302);
    }

    private IssueLinkCodeResult.Issued issueCode(UUID minecraftUniqueId, String minecraftName) {
        return assertInstanceOf(
                IssueLinkCodeResult.Issued.class,
                accountLinkService.issueLinkCode(minecraftUniqueId, minecraftName).join()
        );
    }

    private static void assertStoredCodeIsHashed(UUID minecraftUniqueId, String plainCode) throws Exception {
        String sql = """
                SELECT code_hash
                FROM discord_pending_link_codes
                WHERE minecraft_uuid = ?
                """;
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        ); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUniqueId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                String storedCodeHash = resultSet.getString("code_hash");
                assertEquals(64, storedCodeHash.length());
                assertFalse(storedCodeHash.equalsIgnoreCase(plainCode.replace("-", "")));
            }
        }
    }

    private void createDisposableDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL_ADMIN_URL,
                POSTGRESQL_USER,
                POSTGRESQL_PASSWORD
        ); PreparedStatement existenceStatement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = ?)")) {
            existenceStatement.setString(1, DATABASE_NAME);
            try (ResultSet resultSet = existenceStatement.executeQuery()) {
                resultSet.next();
                if (resultSet.getBoolean(1)) {
                    throw new IllegalStateException(
                            "Refusing to use existing "
                                    + DATABASE_NAME
                                    + " database");
                }
            }
            try (Statement createStatement = connection.createStatement()) {
                createStatement.execute("""
                        CREATE DATABASE discord_link_integration
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
            statement.execute("DROP DATABASE discord_link_integration WITH (FORCE)");
            databaseCreated = false;
        }
    }
}
