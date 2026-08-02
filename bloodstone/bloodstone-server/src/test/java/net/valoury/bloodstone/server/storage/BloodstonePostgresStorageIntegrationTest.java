package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.CombatResolution;
import net.valoury.bloodstone.server.model.AxeFuserOperation;
import net.valoury.bloodstone.server.model.EnchanterOperation;
import net.valoury.bloodstone.server.model.LeaderboardMetric;
import net.valoury.bloodstone.server.model.PlayerProfile;
import net.valoury.bloodstone.server.model.RandomBoxOperation;
import net.valoury.bloodstone.server.model.RecoverableOperationState;
import net.valoury.bloodstone.server.model.RepairOperation;
import net.valoury.bloodstone.server.model.StorageSession;
import net.valoury.bloodstone.server.model.StorageType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIf("integrationTestsEnabled")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class BloodstonePostgresStorageIntegrationTest {

    private static final UUID KILLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VICTIM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ASSIST_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final Instant BASE_TIME = Instant.parse("2026-07-26T10:00:00Z");
    private static final String JDBC_URL =
            BloodstoneStorageIntegrationTestConstants.POSTGRESQL_URL;
    private static final String USERNAME =
            BloodstoneStorageIntegrationTestConstants.POSTGRESQL_USER;
    private static final String PASSWORD =
            BloodstoneStorageIntegrationTestConstants.POSTGRESQL_PASSWORD;

    private BloodstonePostgresStorage storage;

    private static boolean integrationTestsEnabled() {
        return BloodstoneStorageIntegrationTestConstants.ENABLED;
    }

    @BeforeAll
    void initializeFreshSchema() {
        storage = new BloodstonePostgresStorage(JDBC_URL, USERNAME, PASSWORD);
        storage.initialize().join();
    }

    @AfterAll
    void closeStorage() {
        storage.close();
    }

    @Test
    @Order(1)
    void initializesCompleteOwnedSchemaWithoutWholeInventoryPersistence() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                JDBC_URL,
                USERNAME,
                PASSWORD
        );
             Statement statement = connection.createStatement()) {
            assertTrue(tableExists(statement, "bloodstone_players"));
            assertTrue(tableExists(statement, "bloodstone_storage_contents"));
            assertTrue(tableExists(statement, "bloodstone_combat_events"));
            assertTrue(tableExists(
                    statement,
                    "bloodstone_axe_fuser_operations"
            ));
            assertFalse(tableExists(statement, "bloodstone_player_inventories"));
            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'bloodstone_guild_statistics'
                      AND column_name = 'deaths'
                    """)) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
            }
        }

        storage.loadPlayer(KILLER_ID, "Killer").join();
        storage.loadPlayer(VICTIM_ID, "Victim").join();
        storage.loadPlayer(ASSIST_ID, "Assistant").join();
    }

    @Test
    @Order(2)
    void appliesCompoundCombatOnceAndPreservesSameGuildOrdering() {
        UUID eventId = UUID.randomUUID();
        CombatResolution resolution = new CombatResolution(
                eventId,
                KILLER_ID,
                VICTIM_ID,
                ASSIST_ID,
                Set.of(ASSIST_ID),
                GUILD_ID,
                GUILD_ID,
                true,
                true,
                BASE_TIME
        );

        CombatResolutionOutcome first = storage.resolveCombat(resolution).join();
        CombatResolutionOutcome replay = storage.resolveCombat(resolution).join();
        assertTrue(first.newlyApplied());
        assertFalse(replay.newlyApplied());
        assertEquals(first.killerCurrentRampage(), replay.killerCurrentRampage());
        assertEquals(1, first.killerCurrentRampage());
        assertEquals(1, first.killerGuildCurrentRampage());

        PlayerProfile killer = storage.loadPlayer(KILLER_ID, "Killer").join().profile();
        PlayerProfile victim = storage.loadPlayer(VICTIM_ID, "Victim").join().profile();
        PlayerProfile assistant = storage.loadPlayer(ASSIST_ID, "Assistant").join().profile();
        assertEquals(1, killer.kills());
        assertEquals(0, killer.carries());
        assertEquals(1, killer.dominations());
        assertEquals(1, killer.revenges());
        assertEquals(1, victim.deaths());
        assertEquals(1, assistant.assists());
        assertEquals(1, assistant.carries());

        UUID uncreditedEventId = UUID.randomUUID();
        assertTrue(storage.recordDeath(
                uncreditedEventId, VICTIM_ID, GUILD_ID, BASE_TIME.plusSeconds(1)).join());
        assertFalse(storage.recordDeath(
                uncreditedEventId, VICTIM_ID, GUILD_ID, BASE_TIME.plusSeconds(1)).join());
    }

    @Test
    @Order(3)
    void reservesRollingRandomBoxesAndRecoversExactReward() {
        RandomBoxOperation first = reservedRandomBox(
                UUID.randomUUID(), false, BASE_TIME, 2, 36);
        RandomBoxOperation second = reservedRandomBox(
                UUID.randomUUID(), false, BASE_TIME.plusSeconds(1), 2, 36);
        assertTrue(first.freeUse());
        assertTrue(second.freeUse());

        UUID paidOperationId = UUID.randomUUID();
        assertInstanceOf(RandomBoxReserveOutcome.PaymentRequired.class,
                storage.reserveRandomBox(
                        paidOperationId,
                        KILLER_ID,
                        "golden_apple",
                        new byte[]{9, 8, 7},
                        2,
                        36,
                        false,
                        BASE_TIME.plusSeconds(2)
                ).join());
        RandomBoxReserveOutcome.Reserved paid = assertInstanceOf(
                RandomBoxReserveOutcome.Reserved.class,
                storage.reserveRandomBox(
                        paidOperationId,
                        KILLER_ID,
                        "golden_apple",
                        new byte[]{9, 8, 7},
                        2,
                        36,
                        true,
                        BASE_TIME.plusSeconds(2)
                ).join()
        );
        assertFalse(paid.operation().freeUse());
        assertEquals(36, paid.operation().bloodCost());
        assertArrayEquals(new byte[]{9, 8, 7}, paid.operation().rewardPayload());

        RandomBoxOperation reset = reservedRandomBox(
                UUID.randomUUID(),
                false,
                BASE_TIME.plus(Duration.ofHours(24)),
                2,
                36
        );
        assertTrue(reset.freeUse());
        assertTrue(storage.completeRandomBox(paidOperationId, KILLER_ID).join());
        assertTrue(storage.completeRandomBox(paidOperationId, KILLER_ID).join());
        assertInstanceOf(RandomBoxReserveOutcome.AlreadyCompleted.class,
                storage.reserveRandomBox(
                        paidOperationId,
                        KILLER_ID,
                        "golden_apple",
                        new byte[]{9, 8, 7},
                        2,
                        36,
                        true,
                        BASE_TIME
                ).join());
    }

    @Test
    @Order(4)
    void transitionsFeatureSpecificRecoveryOperations() {
        UUID soulboundId = UUID.randomUUID();
        storage.reserveSoulboundRecovery(
                soulboundId, KILLER_ID, new byte[]{1}, BASE_TIME).join();
        assertEquals(1, storage.fetchSoulboundRecoveries(KILLER_ID).join().size());
        assertTrue(storage.completeSoulboundRecovery(soulboundId, KILLER_ID).join());

        UUID enchanterId = UUID.randomUUID();
        EnchanterReserveOutcome.Reserved enchanter = assertInstanceOf(
                EnchanterReserveOutcome.Reserved.class,
                storage.reserveEnchanterOperation(
                        enchanterId,
                        KILLER_ID,
                        "diamond_sword::damage_all",
                        BASE_TIME,
                        Duration.ofMinutes(10),
                        new byte[]{2}
                ).join()
        );
        assertEquals(RecoverableOperationState.RESERVED, enchanter.operation().state());
        assertInstanceOf(EnchanterReserveOutcome.OnCooldown.class,
                storage.reserveEnchanterOperation(
                        UUID.randomUUID(),
                        KILLER_ID,
                        "diamond_sword::damage_all",
                        BASE_TIME.plusSeconds(1),
                        Duration.ofMinutes(10),
                        new byte[]{2}
                ).join());
        assertTrue(storage.markEnchanterOperationReady(
                enchanterId, KILLER_ID, new byte[]{3}).join());
        EnchanterOperation readyEnchanter = storage.fetchEnchanterRecoveries(KILLER_ID)
                .join().stream()
                .filter(operation -> operation.operationId().equals(enchanterId))
                .findFirst()
                .orElseThrow();
        assertEquals(RecoverableOperationState.READY, readyEnchanter.state());
        assertArrayEquals(new byte[]{3}, readyEnchanter.recoveryPayload());
        assertTrue(storage.completeEnchanterOperation(enchanterId, KILLER_ID).join());

        UUID repairId = UUID.randomUUID();
        RepairReserveOutcome.Reserved repair = assertInstanceOf(
                RepairReserveOutcome.Reserved.class,
                storage.reserveRepairOperation(
                        repairId, KILLER_ID, new byte[]{4}, BASE_TIME).join()
        );
        assertEquals(RecoverableOperationState.RESERVED, repair.operation().state());
        assertTrue(storage.markRepairOperationReady(
                repairId, KILLER_ID, new byte[]{5}).join());
        RepairOperation readyRepair = storage.fetchRepairRecoveries(KILLER_ID)
                .join().stream()
                .filter(operation -> operation.operationId().equals(repairId))
                .findFirst()
                .orElseThrow();
        assertArrayEquals(new byte[]{5}, readyRepair.recoveryPayload());
        assertTrue(storage.completeRepairOperation(repairId, KILLER_ID).join());

        UUID axeFuserId = UUID.randomUUID();
        AxeFuserReserveOutcome.Reserved axeFuser = assertInstanceOf(
                AxeFuserReserveOutcome.Reserved.class,
                storage.reserveAxeFuserOperation(
                        axeFuserId,
                        KILLER_ID,
                        new byte[]{6},
                        16,
                        BASE_TIME
                ).join()
        );
        assertEquals(
                RecoverableOperationState.RESERVED,
                axeFuser.operation().state()
        );
        assertEquals(16, axeFuser.operation().bloodAlloyCost());
        assertTrue(storage.markAxeFuserOperationReady(
                axeFuserId,
                KILLER_ID,
                new byte[]{7}
        ).join());
        AxeFuserOperation readyAxeFuser =
                storage.fetchAxeFuserRecoveries(KILLER_ID)
                        .join()
                        .stream()
                        .filter(operation ->
                                operation.operationId().equals(axeFuserId))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                RecoverableOperationState.READY,
                readyAxeFuser.state()
        );
        assertArrayEquals(
                new byte[]{7},
                readyAxeFuser.fusedAxePayload()
        );
        assertTrue(storage.completeAxeFuserOperation(
                axeFuserId,
                KILLER_ID
        ).join());
    }

    @Test
    @Order(5)
    void enforcesStorageLeasesVersionsAndAllSixLeaderboardQueries() {
        UUID sessionToken = UUID.randomUUID();
        StorageOpenOutcome.Opened opened = assertInstanceOf(
                StorageOpenOutcome.Opened.class,
                storage.openStorage(
                        KILLER_ID,
                        StorageType.DEFAULT,
                        sessionToken,
                        BASE_TIME,
                        Duration.ofSeconds(30)
                ).join()
        );
        assertInstanceOf(StorageOpenOutcome.InUse.class,
                storage.openStorage(
                        KILLER_ID,
                        StorageType.DEFAULT,
                        UUID.randomUUID(),
                        BASE_TIME.plusSeconds(1),
                        Duration.ofSeconds(30)
                ).join());

        byte[] contents = new byte[]{6, 7};
        StorageWriteOutcome.Saved checkpoint = assertInstanceOf(
                StorageWriteOutcome.Saved.class,
                storage.checkpointStorage(
                        opened.session(),
                        contents,
                        BASE_TIME.plusSeconds(10),
                        Duration.ofSeconds(30)
                ).join()
        );
        assertInstanceOf(StorageWriteOutcome.SessionConflict.class,
                storage.checkpointStorage(
                        opened.session(),
                        new byte[]{8},
                        BASE_TIME.plusSeconds(11),
                        Duration.ofSeconds(30)
                ).join());
        assertInstanceOf(StorageWriteOutcome.Saved.class,
                storage.closeStorage(
                        checkpoint.session(),
                        contents,
                        BASE_TIME.plusSeconds(12)
                ).join());
        StorageOpenOutcome.Opened reopened = assertInstanceOf(
                StorageOpenOutcome.Opened.class,
                storage.openStorage(
                        KILLER_ID,
                        StorageType.DEFAULT,
                        UUID.randomUUID(),
                        BASE_TIME.plusSeconds(13),
                        Duration.ofSeconds(30)
                ).join()
        );
        assertArrayEquals(contents, reopened.session().contentsPayload());
        storage.closeStorage(reopened.session(), contents, BASE_TIME.plusSeconds(14)).join();

        assertInstanceOf(StorageOpenOutcome.Locked.class,
                storage.openStorage(
                        KILLER_ID,
                        StorageType.EXTRA,
                        UUID.randomUUID(),
                        BASE_TIME,
                        Duration.ofSeconds(30)
                ).join());
        assertInstanceOf(ExtraStorageUnlockOutcome.Unlocked.class,
                storage.unlockExtraStorage(
                        UUID.randomUUID(), KILLER_ID, BASE_TIME).join());
        StorageOpenOutcome.Opened extra = assertInstanceOf(
                StorageOpenOutcome.Opened.class,
                storage.openStorage(
                        KILLER_ID,
                        StorageType.EXTRA,
                        UUID.randomUUID(),
                        BASE_TIME,
                        Duration.ofSeconds(30)
                ).join()
        );
        storage.closeStorage(extra.session(), null, BASE_TIME.plusSeconds(1)).join();

        for (LeaderboardMetric metric : LeaderboardMetric.values()) {
            assertFalse(storage.fetchPlayerLeaderboard(metric).join().isEmpty());
            assertFalse(storage.fetchGuildLeaderboard(metric).join().isEmpty());
        }
    }

    @Test
    @Order(6)
    void writesRenamedPaidStorageUsingItsStablePersistenceKey() {
        byte[] checkpointContents = new byte[]{9, 10};
        StorageOpenOutcome.Opened opened = assertInstanceOf(
                StorageOpenOutcome.Opened.class,
                storage.openStorage(
                        KILLER_ID,
                        StorageType.LEGATE,
                        UUID.randomUUID(),
                        BASE_TIME,
                        Duration.ofSeconds(30)
                ).join()
        );
        StorageWriteOutcome.Saved checkpoint = assertInstanceOf(
                StorageWriteOutcome.Saved.class,
                storage.checkpointStorage(
                        opened.session(),
                        checkpointContents,
                        BASE_TIME.plusSeconds(1),
                        Duration.ofSeconds(30)
                ).join()
        );

        byte[] closedContents = new byte[]{11, 12};
        assertInstanceOf(
                StorageWriteOutcome.Saved.class,
                storage.closeStorage(
                        checkpoint.session(),
                        closedContents,
                        BASE_TIME.plusSeconds(2)
                ).join()
        );
        StorageOpenOutcome.Opened reopened = assertInstanceOf(
                StorageOpenOutcome.Opened.class,
                storage.openStorage(
                        KILLER_ID,
                        StorageType.LEGATE,
                        UUID.randomUUID(),
                        BASE_TIME.plusSeconds(3),
                        Duration.ofSeconds(30)
                ).join()
        );

        assertArrayEquals(closedContents, reopened.session().contentsPayload());
        assertInstanceOf(
                StorageWriteOutcome.Saved.class,
                storage.closeStorage(
                        reopened.session(),
                        closedContents,
                        BASE_TIME.plusSeconds(4)
                ).join()
        );
    }

    @Test
    @Order(7)
    void existingRootTableSuppressesAllSchemaDdl() throws Exception {
        String gateJdbcUrl =
                BloodstoneStorageIntegrationTestConstants.GATE_POSTGRESQL_URL;
        String gateUsername =
                BloodstoneStorageIntegrationTestConstants.GATE_POSTGRESQL_USER;
        String gatePassword =
                BloodstoneStorageIntegrationTestConstants.GATE_POSTGRESQL_PASSWORD;
        try (Connection connection =
                     DriverManager.getConnection(gateJdbcUrl, gateUsername, gatePassword);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bloodstone_players (player_id UUID PRIMARY KEY)");
        }

        BloodstonePostgresStorage gatedStorage =
                new BloodstonePostgresStorage(gateJdbcUrl, gateUsername, gatePassword);
        try {
            assertThrows(
                    CompletionException.class,
                    () -> gatedStorage.initialize().join()
            );
        } finally {
            gatedStorage.close();
        }
        try (Connection connection =
                     DriverManager.getConnection(gateJdbcUrl, gateUsername, gatePassword);
             Statement statement = connection.createStatement()) {
            assertTrue(tableExists(statement, "bloodstone_players"));
            assertFalse(tableExists(statement, "bloodstone_storage_contents"));
            assertFalse(tableExists(statement, "bloodstone_combat_events"));
        }
    }

    private RandomBoxOperation reservedRandomBox(
            UUID operationId,
            boolean paidUseAllowed,
            Instant now,
            int maximumFreeUses,
            int paidCost
    ) {
        RandomBoxReserveOutcome.Reserved reserved = assertInstanceOf(
                RandomBoxReserveOutcome.Reserved.class,
                storage.reserveRandomBox(
                        operationId,
                        KILLER_ID,
                        "golden_apple",
                        new byte[]{9, 8, 7},
                        maximumFreeUses,
                        paidCost,
                        paidUseAllowed,
                        now
                ).join()
        );
        return reserved.operation();
    }

    private boolean tableExists(Statement statement, String tableName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT to_regclass('public." + tableName + "') IS NOT NULL")) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }
}
