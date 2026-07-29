package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.RepairOperation;

public sealed interface RepairReserveOutcome permits
        RepairReserveOutcome.Reserved {

    record Reserved(RepairOperation operation) implements RepairReserveOutcome {
    }

}
