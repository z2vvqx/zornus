package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OperationCoordinationTest {

    @Test
    void globalCapacityAllowsExactlyFourConcurrentOperations() {
        OperationCapacity capacity = new OperationCapacity(4);
        UUID firstOperationId = UUID.randomUUID();

        assertTrue(capacity.tryBegin(firstOperationId));
        assertTrue(capacity.tryBegin(UUID.randomUUID()));
        assertTrue(capacity.tryBegin(UUID.randomUUID()));
        assertTrue(capacity.tryBegin(UUID.randomUUID()));
        assertFalse(capacity.hasAvailability());
        assertFalse(capacity.tryBegin(UUID.randomUUID()));

        capacity.finish(firstOperationId);
        assertTrue(capacity.hasAvailability());
        assertTrue(capacity.tryBegin(UUID.randomUUID()));
    }

    @Test
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new OperationCapacity(0));
    }

    @Test
    void randomBoxResourceRemainsExclusiveUntilItsOperationFinishes() {
        ExclusiveOperationResources<String> activeBlocks =
                new ExclusiveOperationResources<>();
        UUID firstOperationId = UUID.randomUUID();

        assertTrue(activeBlocks.tryBegin("first-block", firstOperationId));
        assertFalse(activeBlocks.tryBegin("first-block", UUID.randomUUID()));
        assertTrue(activeBlocks.tryBegin("second-block", UUID.randomUUID()));

        activeBlocks.finish("first-block", UUID.randomUUID());
        assertFalse(activeBlocks.tryBegin("first-block", UUID.randomUUID()));

        activeBlocks.finish("first-block", firstOperationId);
        assertTrue(activeBlocks.tryBegin("first-block", UUID.randomUUID()));
    }

    @Test
    void randomBoxSuppressesOnlyItsPlayersDuplicatePendingClicks() {
        RandomBoxOperationCoordinator<String> activeBoxes =
                new RandomBoxOperationCoordinator<>();
        UUID firstOperationId = UUID.randomUUID();
        UUID firstPlayerId = UUID.randomUUID();

        assertEquals(
                RandomBoxOperationCoordinator.BeginOutcome.STARTED,
                activeBoxes.tryBegin(
                        "first-block",
                        firstOperationId,
                        firstPlayerId
                )
        );
        assertEquals(
                RandomBoxOperationCoordinator.BeginOutcome
                        .ALREADY_PENDING_BY_PLAYER,
                activeBoxes.tryBegin(
                        "first-block",
                        UUID.randomUUID(),
                        firstPlayerId
                )
        );
        assertEquals(
                RandomBoxOperationCoordinator.BeginOutcome.RESOURCE_IN_USE,
                activeBoxes.tryBegin(
                        "first-block",
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );
    }

    @Test
    void activeRandomBoxRejectsEveryDuplicateClickUntilExactFinish() {
        RandomBoxOperationCoordinator<String> activeBoxes =
                new RandomBoxOperationCoordinator<>();
        UUID operationId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        assertEquals(
                RandomBoxOperationCoordinator.BeginOutcome.STARTED,
                activeBoxes.tryBegin("first-block", operationId, playerId)
        );
        activeBoxes.activate("first-block", UUID.randomUUID());
        assertEquals(
                RandomBoxOperationCoordinator.BeginOutcome
                        .ALREADY_PENDING_BY_PLAYER,
                activeBoxes.tryBegin(
                        "first-block",
                        UUID.randomUUID(),
                        playerId
                )
        );

        activeBoxes.activate("first-block", operationId);
        assertEquals(
                RandomBoxOperationCoordinator.BeginOutcome.RESOURCE_IN_USE,
                activeBoxes.tryBegin(
                        "first-block",
                        UUID.randomUUID(),
                        playerId
                )
        );

        activeBoxes.finish("first-block", UUID.randomUUID());
        assertEquals(
                RandomBoxOperationCoordinator.BeginOutcome.RESOURCE_IN_USE,
                activeBoxes.tryBegin(
                        "first-block",
                        UUID.randomUUID(),
                        playerId
                )
        );

        activeBoxes.finish("first-block", operationId);
        assertEquals(
                RandomBoxOperationCoordinator.BeginOutcome.STARTED,
                activeBoxes.tryBegin(
                        "first-block",
                        UUID.randomUUID(),
                        playerId
                )
        );
    }
}
