package com.zornus.parties.proxy.storage;

public sealed interface JoinOutcome permits
        JoinOutcome.Joined,
        JoinOutcome.PartyFull,
        JoinOutcome.AlreadyMember,
        JoinOutcome.InvitationExpired,
        JoinOutcome.InvitationNoLongerValid {
    record Joined() implements JoinOutcome {}
    record PartyFull() implements JoinOutcome {}
    record AlreadyMember() implements JoinOutcome {}
    record InvitationExpired() implements JoinOutcome {}
    record InvitationNoLongerValid() implements JoinOutcome {}
}
