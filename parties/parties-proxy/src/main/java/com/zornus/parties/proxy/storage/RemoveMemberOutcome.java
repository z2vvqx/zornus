package com.zornus.parties.proxy.storage;

import java.util.UUID;

public sealed interface RemoveMemberOutcome permits
        RemoveMemberOutcome.MemberRemoved,
        RemoveMemberOutcome.LeaderTransferred,
        RemoveMemberOutcome.PartyDisbanded,
        RemoveMemberOutcome.MemberNotFound,
        RemoveMemberOutcome.PartyNotFound {
    record MemberRemoved() implements RemoveMemberOutcome {}
    record LeaderTransferred(UUID newLeaderId) implements RemoveMemberOutcome {}
    record PartyDisbanded() implements RemoveMemberOutcome {}
    record MemberNotFound() implements RemoveMemberOutcome {}
    record PartyNotFound() implements RemoveMemberOutcome {}
}
