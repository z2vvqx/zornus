package net.valoury.bloodstone.server.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class OperationCapacity {

    private final int maximumConcurrentOperations;
    private final Set<UUID> activeOperationIds = new HashSet<>();

    OperationCapacity(int maximumConcurrentOperations) {
        if (maximumConcurrentOperations < 1) {
            throw new IllegalArgumentException(
                    "Maximum concurrent operations must be positive"
            );
        }
        this.maximumConcurrentOperations = maximumConcurrentOperations;
    }

    boolean hasAvailability() {
        return activeOperationIds.size() < maximumConcurrentOperations;
    }

    boolean tryBegin(UUID operationId) {
        if (!hasAvailability()) {
            return false;
        }
        return activeOperationIds.add(operationId);
    }

    void finish(UUID operationId) {
        activeOperationIds.remove(operationId);
    }

    void clear() {
        activeOperationIds.clear();
    }
}
