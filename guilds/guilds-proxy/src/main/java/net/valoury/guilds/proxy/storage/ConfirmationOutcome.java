package net.valoury.guilds.proxy.storage;

import net.valoury.guilds.proxy.model.PendingConfirmation;

public sealed interface ConfirmationOutcome permits
        ConfirmationOutcome.Set,
        ConfirmationOutcome.AlreadyExists {
    record Set() implements ConfirmationOutcome {
    }

    record AlreadyExists(PendingConfirmation existing) implements ConfirmationOutcome {
    }
}
