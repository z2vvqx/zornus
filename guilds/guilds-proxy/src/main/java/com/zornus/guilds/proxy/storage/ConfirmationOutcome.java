package com.zornus.guilds.proxy.storage;

import com.zornus.guilds.proxy.model.PendingConfirmation;

public sealed interface ConfirmationOutcome permits
        ConfirmationOutcome.Set,
        ConfirmationOutcome.AlreadyExists {
    record Set() implements ConfirmationOutcome {}
    record AlreadyExists(PendingConfirmation existing) implements ConfirmationOutcome {}
}
