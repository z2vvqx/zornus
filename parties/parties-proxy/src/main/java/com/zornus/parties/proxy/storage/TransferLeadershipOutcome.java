package com.zornus.parties.proxy.storage;

public sealed interface TransferLeadershipOutcome permits
        TransferLeadershipOutcome.Transferred,
        TransferLeadershipOutcome.PartyNotFound,
        TransferLeadershipOutcome.TargetNotMember {
    record Transferred() implements TransferLeadershipOutcome {}
    record PartyNotFound() implements TransferLeadershipOutcome {}
    record TargetNotMember() implements TransferLeadershipOutcome {}
}
