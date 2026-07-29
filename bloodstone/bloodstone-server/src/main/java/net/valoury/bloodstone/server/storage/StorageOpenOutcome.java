package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.StorageSession;

import java.time.Instant;

public sealed interface StorageOpenOutcome permits
        StorageOpenOutcome.Opened,
        StorageOpenOutcome.InUse,
        StorageOpenOutcome.Locked,
        StorageOpenOutcome.PlayerNotFound {

    record Opened(StorageSession session) implements StorageOpenOutcome {
    }

    record InUse(Instant leaseExpiresAt) implements StorageOpenOutcome {
    }

    record Locked() implements StorageOpenOutcome {
    }

    record PlayerNotFound() implements StorageOpenOutcome {
    }
}
