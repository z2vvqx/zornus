package com.zornus.parties.proxy.storage;

import com.zornus.parties.proxy.model.PendingConfirmation;

public sealed interface ConfirmationOutcome permits
        ConfirmationOutcome.Set,
        ConfirmationOutcome.AlreadyExists {
    record Set() implements ConfirmationOutcome {}
    record AlreadyExists(PendingConfirmation existing) implements ConfirmationOutcome {}
}
