package com.zornus.guilds.proxy.storage;

import java.util.UUID;

public sealed interface RemoveMemberOutcome permits
        RemoveMemberOutcome.MemberRemoved,
        RemoveMemberOutcome.LeaderTransferred,
        RemoveMemberOutcome.GuildDisbanded,
        RemoveMemberOutcome.MemberNotFound,
        RemoveMemberOutcome.GuildNotFound,
        RemoveMemberOutcome.CannotRemoveLeader {
    record MemberRemoved() implements RemoveMemberOutcome {}
    record LeaderTransferred(UUID newLeaderId) implements RemoveMemberOutcome {}
    record GuildDisbanded() implements RemoveMemberOutcome {}
    record MemberNotFound() implements RemoveMemberOutcome {}
    record GuildNotFound() implements RemoveMemberOutcome {}
    record CannotRemoveLeader() implements RemoveMemberOutcome {}
}
