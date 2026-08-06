package net.valoury.staff.proxy.storage;

import net.valoury.staff.proxy.StaffProxyConstants;
import net.valoury.staff.proxy.model.AddressFingerprint;
import net.valoury.staff.proxy.model.ConnectionEdge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StaffPostgresStorageIntegrationTest {
    private static final String DATABASE_URL_PROPERTY = "staff.database.url";
    private static final UUID TARGET_PLAYER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DIRECT_PLAYER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID INDIRECT_PLAYER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");

    private String databaseUrl;
    private String schemaName;
    private String schemaDatabaseUrl;
    private StaffPostgresStorage storage;

    @BeforeEach
    void setUp() throws SQLException {
        databaseUrl = System.getProperty(DATABASE_URL_PROPERTY);
        assumeTrue(databaseUrl != null && !databaseUrl.isBlank());
        schemaName = "staff_storage_test_"
                + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schemaName);
        }
        schemaDatabaseUrl = databaseUrl
                + (databaseUrl.contains("?") ? "&" : "?")
                + "currentSchema=" + schemaName;
        storage = new StaffPostgresStorage(
                schemaDatabaseUrl,
                StaffProxyConstants.POSTGRESQL_USER,
                StaffProxyConstants.POSTGRESQL_PASSWORD
        );
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (storage != null) {
            storage.close();
        }
        if (schemaName != null) {
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schemaName + " CASCADE");
            }
        }
    }

    @Test
    void recordsAndTraversesConnectionsWhileEnforcingRetention() throws SQLException {
        AddressFingerprint firstAddress = fingerprint('0');
        AddressFingerprint sharedAddress = fingerprint('1');
        AddressFingerprint indirectAddress = fingerprint('2');

        storage.recordConnection(TARGET_PLAYER_UUID, "Target", firstAddress).join();
        storage.recordConnection(TARGET_PLAYER_UUID, "Target", sharedAddress).join();
        storage.recordConnection(DIRECT_PLAYER_UUID, "Direct", sharedAddress).join();
        storage.recordConnection(DIRECT_PLAYER_UUID, "Direct", indirectAddress).join();
        storage.recordConnection(INDIRECT_PLAYER_UUID, "Indirect", indirectAddress).join();

        List<ConnectionEdge> component = storage
                .fetchConnectedComponent(TARGET_PLAYER_UUID)
                .join();
        assertEquals(5, component.size());
        assertEquals(
                3,
                component.stream().map(ConnectionEdge::playerUuid).distinct().count()
        );
        assertEquals(
                3,
                component.stream()
                        .map(ConnectionEdge::addressFingerprint)
                        .distinct()
                        .count()
        );
        assertTrue(storage.fetchPlayerByUsername("target").join().isPresent());

        assertThrows(SQLException.class, this::insertObservationExpiringAfterRetention);
        insertExpiredObservation();
        storage.cleanupExpiredConnections().join();
        assertEquals(0, countObservationsForUsername("Expired"));
    }

    private void insertObservationExpiringAfterRetention() throws SQLException {
        String sql = """
                INSERT INTO staff_connection_observations (
                    player_id,
                    username,
                    address_fingerprint,
                    observed_at,
                    expires_at
                )
                VALUES (?, 'TooLong', ?, NOW(), NOW() + INTERVAL '31 days')
                """;
        try (Connection connection = openSchemaConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, fingerprint('3').encodedValue());
            statement.executeUpdate();
        }
    }

    private void insertExpiredObservation() throws SQLException {
        String sql = """
                INSERT INTO staff_connection_observations (
                    player_id,
                    username,
                    address_fingerprint,
                    observed_at,
                    expires_at
                )
                VALUES (
                    ?,
                    'Expired',
                    ?,
                    NOW() - INTERVAL '31 days',
                    NOW() - INTERVAL '1 day'
                )
                """;
        try (Connection connection = openSchemaConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, fingerprint('4').encodedValue());
            statement.executeUpdate();
        }
    }

    private int countObservationsForUsername(String username) throws SQLException {
        try (Connection connection = openSchemaConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM staff_connection_observations WHERE username = ?"
             )) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                databaseUrl,
                StaffProxyConstants.POSTGRESQL_USER,
                StaffProxyConstants.POSTGRESQL_PASSWORD
        );
    }

    private Connection openSchemaConnection() throws SQLException {
        return DriverManager.getConnection(
                schemaDatabaseUrl,
                StaffProxyConstants.POSTGRESQL_USER,
                StaffProxyConstants.POSTGRESQL_PASSWORD
        );
    }

    private static AddressFingerprint fingerprint(char character) {
        return new AddressFingerprint(String.valueOf(character).repeat(52));
    }
}
