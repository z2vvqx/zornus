package net.valoury.bloodstone.server.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OperationCoordinationTest {

    @Test
    void playerCapacityCombinesTenOperationsWithoutLimitingOtherPlayers() {
        PlayerOperationCapacity capacity = new PlayerOperationCapacity(10);
        UUID firstPlayerId = UUID.randomUUID();
        UUID secondPlayerId = UUID.randomUUID();
        UUID[] firstPlayerOperationIds = new UUID[10];

        for (int index = 0; index < firstPlayerOperationIds.length; index++) {
            UUID operationId = UUID.randomUUID();
            firstPlayerOperationIds[index] = operationId;
            assertTrue(capacity.tryBegin(firstPlayerId, operationId));
        }

        assertFalse(capacity.hasAvailability(firstPlayerId));
        assertFalse(capacity.tryBegin(firstPlayerId, UUID.randomUUID()));
        assertTrue(capacity.hasAvailability(secondPlayerId));
        assertTrue(capacity.tryBegin(secondPlayerId, UUID.randomUUID()));

        capacity.finish(firstPlayerOperationIds[0]);
        assertTrue(capacity.hasAvailability(firstPlayerId));
        assertTrue(capacity.tryBegin(firstPlayerId, UUID.randomUUID()));
    }

    @Test
    void playerCapacityRejectsDuplicateOperationIds() {
        PlayerOperationCapacity capacity = new PlayerOperationCapacity(10);
        UUID operationId = UUID.randomUUID();

        assertTrue(capacity.tryBegin(UUID.randomUUID(), operationId));
        assertFalse(capacity.tryBegin(UUID.randomUUID(), operationId));
    }

    @Test
    void playerCapacityMustBePositive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerOperationCapacity(0)
        );
    }

    @Test
    void fuserLimitsOneOperationPerPlayerAndPerBlock() {
        ExclusiveOperationResources<String> activeBlocks =
                new ExclusiveOperationResources<>();
        ExclusiveOperationResources<UUID> activePlayers =
                new ExclusiveOperationResources<>();
        UUID firstPlayerId = UUID.randomUUID();
        UUID secondPlayerId = UUID.randomUUID();
        UUID firstOperationId = UUID.randomUUID();

        assertTrue(activeBlocks.tryBegin("first-block", firstOperationId));
        assertTrue(activePlayers.tryBegin(firstPlayerId, firstOperationId));

        UUID samePlayerOperationId = UUID.randomUUID();
        assertTrue(activeBlocks.tryBegin("second-block", samePlayerOperationId));
        assertFalse(activePlayers.tryBegin(
                firstPlayerId,
                samePlayerOperationId
        ));
        activeBlocks.finish("second-block", samePlayerOperationId);

        assertFalse(activeBlocks.tryBegin(
                "first-block",
                UUID.randomUUID()
        ));

        UUID concurrentOperationId = UUID.randomUUID();
        assertTrue(activeBlocks.tryBegin("second-block", concurrentOperationId));
        assertTrue(activePlayers.tryBegin(
                secondPlayerId,
                concurrentOperationId
        ));

        activeBlocks.finish("first-block", firstOperationId);
        activePlayers.finish(firstPlayerId, firstOperationId);
        UUID resumedOperationId = UUID.randomUUID();
        assertTrue(activeBlocks.tryBegin("third-block", resumedOperationId));
        assertTrue(activePlayers.tryBegin(firstPlayerId, resumedOperationId));
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
