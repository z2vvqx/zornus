package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.EnchanterOperation;

import java.time.Instant;

public sealed interface EnchanterReserveOutcome permits
        EnchanterReserveOutcome.Reserved,
        EnchanterReserveOutcome.OnCooldown {

    record Reserved(EnchanterOperation operation) implements EnchanterReserveOutcome {
    }

    record OnCooldown(Instant availableAt) implements EnchanterReserveOutcome {
    }

}
