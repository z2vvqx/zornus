package com.zornus.parties.proxy.storage;

public sealed interface JoinOutcome permits
        JoinOutcome.Joined,
        JoinOutcome.PartyFull,
        JoinOutcome.AlreadyMember {
    record Joined() implements JoinOutcome {}
    record PartyFull() implements JoinOutcome {}
    record AlreadyMember() implements JoinOutcome {}
}
