package net.valoury.bloodstone.server.storage;

import net.valoury.bloodstone.server.model.RandomBoxOperation;

public sealed interface RandomBoxReserveOutcome permits
        RandomBoxReserveOutcome.Reserved,
        RandomBoxReserveOutcome.AlreadyCompleted,
        RandomBoxReserveOutcome.PaymentRequired {

    record Reserved(RandomBoxOperation operation) implements RandomBoxReserveOutcome {
    }

    record AlreadyCompleted() implements RandomBoxReserveOutcome {
    }

    record PaymentRequired() implements RandomBoxReserveOutcome {
    }

}
