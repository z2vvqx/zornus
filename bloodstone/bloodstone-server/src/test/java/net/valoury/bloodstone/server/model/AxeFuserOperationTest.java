package net.valoury.bloodstone.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AxeFuserOperationTest {

    @Test
    void reservedItemMarkersAreStableAndDistinct() {
        UUID operationId = UUID.randomUUID();

        assertEquals(
                AxeFuserOperation.reservedItemMarker(operationId, 0),
                AxeFuserOperation.reservedItemMarker(operationId, 0)
        );
        assertNotEquals(
                AxeFuserOperation.reservedItemMarker(operationId, 0),
                AxeFuserOperation.reservedItemMarker(operationId, 1)
        );
        assertNotEquals(
                AxeFuserOperation.reservedItemMarker(operationId, 1),
                AxeFuserOperation.reservedItemMarker(operationId, 2)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> AxeFuserOperation.reservedItemMarker(operationId, 3)
        );
    }

    @Test
    void operationPayloadsRemainImmutable() {
        byte[] originals = {1, 2};
        byte[] result = {3, 4};
        AxeFuserOperation operation = new AxeFuserOperation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                originals,
                16,
                result,
                RecoverableOperationState.READY,
                Instant.EPOCH
        );

        originals[0] = 9;
        result[0] = 9;
        assertArrayEquals(new byte[]{1, 2}, operation.originalAxesPayload());
        assertArrayEquals(new byte[]{3, 4}, operation.fusedAxePayload());

        byte[] returnedOriginals = operation.originalAxesPayload();
        byte[] returnedResult = operation.fusedAxePayload();
        returnedOriginals[0] = 8;
        returnedResult[0] = 8;
        assertArrayEquals(new byte[]{1, 2}, operation.originalAxesPayload());
        assertArrayEquals(new byte[]{3, 4}, operation.fusedAxePayload());
    }
}
