package com.zornus.parties.proxy.storage;

public sealed interface DisbandPartyOutcome permits
        DisbandPartyOutcome.Disbanded,
        DisbandPartyOutcome.PartyNotFound {
    record Disbanded() implements DisbandPartyOutcome {}
    record PartyNotFound() implements DisbandPartyOutcome {}
}
