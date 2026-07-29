package net.valoury.bloodstone.server.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ExclusiveOperationResources<Resource> {

    private final Map<Resource, UUID> operationsByResource = new HashMap<>();

    boolean tryBegin(Resource resource, UUID operationId) {
        return operationsByResource.putIfAbsent(resource, operationId) == null;
    }

    void finish(Resource resource, UUID operationId) {
        operationsByResource.remove(resource, operationId);
    }

    void clear() {
        operationsByResource.clear();
    }
}
