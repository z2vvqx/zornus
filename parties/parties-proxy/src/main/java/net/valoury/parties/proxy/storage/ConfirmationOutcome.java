package net.valoury.parties.proxy.storage;

import net.valoury.parties.proxy.model.PendingConfirmation;

public sealed interface ConfirmationOutcome permits
        ConfirmationOutcome.Set,
        ConfirmationOutcome.AlreadyExists {
    record Set() implements ConfirmationOutcome {
    }

    record AlreadyExists(PendingConfirmation existing) implements ConfirmationOutcome {
    }
}
