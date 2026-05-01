package com.zornus.parties.proxy.storage;

public sealed interface WarpOutcome permits
        WarpOutcome.Allowed,
        WarpOutcome.OnCooldown,
        WarpOutcome.PartyNotFound {
    record Allowed() implements WarpOutcome {}
    record OnCooldown() implements WarpOutcome {}
    record PartyNotFound() implements WarpOutcome {}
}
