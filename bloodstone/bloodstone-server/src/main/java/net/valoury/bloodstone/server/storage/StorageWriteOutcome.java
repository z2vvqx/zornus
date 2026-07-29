package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.StorageSession;

public sealed interface StorageWriteOutcome permits
        StorageWriteOutcome.Saved,
        StorageWriteOutcome.SessionConflict {

    record Saved(StorageSession session) implements StorageWriteOutcome {
    }

    record SessionConflict() implements StorageWriteOutcome {
    }
}
