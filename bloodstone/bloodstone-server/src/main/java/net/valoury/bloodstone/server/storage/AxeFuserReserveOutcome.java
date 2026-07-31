package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.AxeFuserOperation;

public sealed interface AxeFuserReserveOutcome permits
        AxeFuserReserveOutcome.Reserved {

    record Reserved(AxeFuserOperation operation) implements AxeFuserReserveOutcome {
    }
}
