package com.zornus.parties.proxy.storage;

public sealed interface CreatePartyOutcome permits
        CreatePartyOutcome.Created,
        CreatePartyOutcome.AlreadyInParty {
    record Created() implements CreatePartyOutcome {}
    record AlreadyInParty() implements CreatePartyOutcome {}
}
