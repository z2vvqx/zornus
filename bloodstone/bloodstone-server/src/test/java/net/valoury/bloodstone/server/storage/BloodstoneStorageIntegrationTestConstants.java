package net.valoury.bloodstone.server.storage;

final class BloodstoneStorageIntegrationTestConstants {

    static final boolean ENABLED = false;

    static final String POSTGRESQL_URL =
            "jdbc:postgresql://localhost:5432/bloodstone_integration";
    static final String POSTGRESQL_USER = "postgres";
    static final String POSTGRESQL_PASSWORD = "postword";

    static final String GATE_POSTGRESQL_URL =
            "jdbc:postgresql://localhost:5432/bloodstone_integration_gate";
    static final String GATE_POSTGRESQL_USER = "postgres";
    static final String GATE_POSTGRESQL_PASSWORD = "postword";

    private BloodstoneStorageIntegrationTestConstants() {
        throw new UnsupportedOperationException(
                "Constants class cannot be instantiated"
        );
    }
}
