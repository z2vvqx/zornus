package net.valoury.bloodstone.server.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class RandomBoxOperationCoordinator<Resource> {

    private final Map<Resource, RandomBoxOperation> operationsByResource =
            new HashMap<>();

    BeginOutcome tryBegin(
            Resource resource,
            UUID operationId,
            UUID playerId
    ) {
        RandomBoxOperation operation =
                new RandomBoxOperation(operationId, playerId, true);
        RandomBoxOperation existing =
                operationsByResource.putIfAbsent(resource, operation);
        if (existing == null) {
            return BeginOutcome.STARTED;
        }
        if (existing.pending() && existing.playerId().equals(playerId)) {
            return BeginOutcome.ALREADY_PENDING_BY_PLAYER;
        }
        return BeginOutcome.RESOURCE_IN_USE;
    }

    void activate(Resource resource, UUID operationId) {
        RandomBoxOperation operation = operationsByResource.get(resource);
        if (operation == null || !operation.operationId().equals(operationId)) {
            return;
        }
        operationsByResource.replace(
                resource,
                operation,
                new RandomBoxOperation(
                        operation.operationId(),
                        operation.playerId(),
                        false
                )
        );
    }

    void finish(Resource resource, UUID operationId) {
        RandomBoxOperation operation = operationsByResource.get(resource);
        if (operation != null && operation.operationId().equals(operationId)) {
            operationsByResource.remove(resource, operation);
        }
    }

    void clear() {
        operationsByResource.clear();
    }

    enum BeginOutcome {
        STARTED,
        ALREADY_PENDING_BY_PLAYER,
        RESOURCE_IN_USE
    }

    private record RandomBoxOperation(
            UUID operationId,
            UUID playerId,
            boolean pending
    ) {
    }
}
