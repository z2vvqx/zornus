package net.valoury.bloodstone.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RecoverableOperationTest {

    @Test
    void enchanterRecoverySelectsOriginalUntilReady() {
        assertArrayEquals(new byte[]{1}, enchanter(RecoverableOperationState.RESERVED).recoveryPayload());
        assertArrayEquals(new byte[]{2}, enchanter(RecoverableOperationState.READY).recoveryPayload());
    }

    @Test
    void repairRecoverySelectsOriginalUntilReady() {
        assertArrayEquals(new byte[]{1}, repair(RecoverableOperationState.RESERVED).recoveryPayload());
        assertArrayEquals(new byte[]{2}, repair(RecoverableOperationState.READY).recoveryPayload());
    }

    @Test
    void readyOperationsRequireAResultPayload() {
        assertThrows(IllegalArgumentException.class, () -> new EnchanterOperation(
                UUID.randomUUID(), UUID.randomUUID(), new byte[]{1}, null,
                RecoverableOperationState.READY, Instant.EPOCH
        ));
        assertThrows(IllegalArgumentException.class, () -> new RepairOperation(
                UUID.randomUUID(), UUID.randomUUID(), new byte[]{1}, null,
                RecoverableOperationState.READY, Instant.EPOCH
        ));
    }

    private EnchanterOperation enchanter(RecoverableOperationState state) {
        return new EnchanterOperation(
                UUID.randomUUID(), UUID.randomUUID(), new byte[]{1},
                state == RecoverableOperationState.READY ? new byte[]{2} : null,
                state, Instant.EPOCH
        );
    }

    private RepairOperation repair(RecoverableOperationState state) {
        return new RepairOperation(
                UUID.randomUUID(), UUID.randomUUID(), new byte[]{1},
                state == RecoverableOperationState.READY ? new byte[]{2} : null,
                state, Instant.EPOCH
        );
    }
}
