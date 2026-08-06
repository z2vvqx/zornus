package net.valoury.staff.proxy.service;

import net.valoury.shared.model.PlayerRecord;
import net.valoury.staff.proxy.model.AddressFingerprint;
import net.valoury.staff.proxy.model.ConnectionEdge;
import net.valoury.staff.proxy.model.ConnectionSummary;
import net.valoury.staff.proxy.model.RelatedAccount;
import net.valoury.staff.proxy.model.StaffInspection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffServiceGraphTest {
    private static final UUID TARGET_PLAYER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DIRECT_PLAYER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID INDIRECT_PLAYER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final AddressFingerprint FIRST_ADDRESS = fingerprint('0');
    private static final AddressFingerprint SHARED_ADDRESS = fingerprint('1');
    private static final AddressFingerprint INDIRECT_ADDRESS = fingerprint('2');
    private static final Instant BASE_TIME = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void buildsDirectAndTransitiveRelationshipsWithoutPersistedRelationRows() {
        List<ConnectionEdge> component = List.of(
                edge(TARGET_PLAYER_UUID, "Target", FIRST_ADDRESS, -20, -10, 2),
                edge(TARGET_PLAYER_UUID, "Target", SHARED_ADDRESS, -8, -1, 3),
                edge(DIRECT_PLAYER_UUID, "Direct", SHARED_ADDRESS, -7, -2, 4),
                edge(DIRECT_PLAYER_UUID, "Direct", INDIRECT_ADDRESS, -6, -3, 2),
                edge(INDIRECT_PLAYER_UUID, "Indirect", INDIRECT_ADDRESS, -5, -4, 1)
        );

        StaffInspection inspection = StaffService.createInspection(
                new PlayerRecord(TARGET_PLAYER_UUID, "Target"),
                component
        );

        assertEquals(5, inspection.connectionCount());
        assertEquals(2, inspection.connections().size());
        assertEquals(BASE_TIME.minusSeconds(20), inspection.firstSeenAt().orElseThrow());
        assertEquals(BASE_TIME.minusSeconds(1), inspection.lastSeenAt().orElseThrow());

        ConnectionSummary sharedConnection = inspection.connections().stream()
                .filter(connection -> connection.addressFingerprint().equals(SHARED_ADDRESS))
                .findFirst()
                .orElseThrow();
        assertEquals(2, sharedConnection.associatedAccountCount());
        assertEquals(3, sharedConnection.connectionCount());

        assertEquals(2, inspection.relatedAccounts().size());
        RelatedAccount directAccount = inspection.relatedAccounts().getFirst();
        assertEquals(DIRECT_PLAYER_UUID, directAccount.playerUuid());
        assertTrue(directAccount.direct());
        assertEquals(1, directAccount.directlySharedConnectionCount());
        assertEquals(
                Set.of(SHARED_ADDRESS),
                directAccount.directlySharedAddressFingerprints()
        );
        assertEquals(BASE_TIME.minusSeconds(7), directAccount.firstSeenAt());
        assertEquals(BASE_TIME.minusSeconds(2), directAccount.lastSeenAt());

        RelatedAccount indirectAccount = inspection.relatedAccounts().getLast();
        assertEquals(INDIRECT_PLAYER_UUID, indirectAccount.playerUuid());
        assertFalse(indirectAccount.direct());
        assertEquals(Set.of(), indirectAccount.directlySharedAddressFingerprints());
        assertEquals(2, indirectAccount.connectionDepth());
        assertEquals("Direct", indirectAccount.connectedThroughUsername());
        assertEquals(BASE_TIME.minusSeconds(5), indirectAccount.firstSeenAt());
        assertEquals(BASE_TIME.minusSeconds(4), indirectAccount.lastSeenAt());
    }

    @Test
    void leavesAnAccountIsolatedWhenItSharesNoAddressFingerprint() {
        StaffInspection inspection = StaffService.createInspection(
                new PlayerRecord(TARGET_PLAYER_UUID, "Target"),
                List.of(edge(
                        TARGET_PLAYER_UUID,
                        "Target",
                        FIRST_ADDRESS,
                        -2,
                        -1,
                        1
                ))
        );

        assertEquals(List.of(), inspection.relatedAccounts());
        assertEquals(1, inspection.connections().size());
        assertEquals(1, inspection.connectionCount());
    }

    @Test
    void handlesConnectionCyclesWithoutRepeatingAccounts() {
        List<ConnectionEdge> component = List.of(
                edge(TARGET_PLAYER_UUID, "Target", FIRST_ADDRESS, -9, -8, 1),
                edge(DIRECT_PLAYER_UUID, "Direct", FIRST_ADDRESS, -8, -7, 1),
                edge(DIRECT_PLAYER_UUID, "Direct", SHARED_ADDRESS, -7, -6, 1),
                edge(INDIRECT_PLAYER_UUID, "Indirect", SHARED_ADDRESS, -6, -5, 1),
                edge(INDIRECT_PLAYER_UUID, "Indirect", INDIRECT_ADDRESS, -5, -4, 1),
                edge(TARGET_PLAYER_UUID, "Target", INDIRECT_ADDRESS, -4, -3, 1)
        );

        StaffInspection inspection = StaffService.createInspection(
                new PlayerRecord(TARGET_PLAYER_UUID, "Target"),
                component
        );

        assertEquals(2, inspection.relatedAccounts().size());
        assertTrue(inspection.relatedAccounts().stream().allMatch(RelatedAccount::direct));
    }

    private static ConnectionEdge edge(
            UUID playerUuid,
            String username,
            AddressFingerprint addressFingerprint,
            long firstSeenOffsetSeconds,
            long lastSeenOffsetSeconds,
            long connectionCount
    ) {
        return new ConnectionEdge(
                playerUuid,
                username,
                addressFingerprint,
                BASE_TIME.plusSeconds(firstSeenOffsetSeconds),
                BASE_TIME.plusSeconds(lastSeenOffsetSeconds),
                connectionCount
        );
    }

    private static AddressFingerprint fingerprint(char character) {
        return new AddressFingerprint(String.valueOf(character).repeat(52));
    }
}
