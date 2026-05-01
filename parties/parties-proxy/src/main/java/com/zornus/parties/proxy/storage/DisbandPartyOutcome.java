package com.zornus.parties.proxy.storage;

public sealed interface DisbandPartyOutcome permits
        DisbandPartyOutcome.Disbanded,
        DisbandPartyOutcome.PartyNotFound,
        DisbandPartyOutcome.NotLeader {
    record Disbanded() implements DisbandPartyOutcome {}
    record PartyNotFound() implements DisbandPartyOutcome {}
    record NotLeader() implements DisbandPartyOutcome {}
}
