package com.zornus.guilds.proxy.storage;

public sealed interface RemoveMemberOutcome permits
        RemoveMemberOutcome.MemberRemoved,
        RemoveMemberOutcome.GuildDisbanded,
        RemoveMemberOutcome.MemberNotFound,
        RemoveMemberOutcome.GuildNotFound,
        RemoveMemberOutcome.CannotRemoveLeader,
        RemoveMemberOutcome.NotLeader {
    record MemberRemoved() implements RemoveMemberOutcome {}
    record GuildDisbanded() implements RemoveMemberOutcome {}
    record MemberNotFound() implements RemoveMemberOutcome {}
    record GuildNotFound() implements RemoveMemberOutcome {}
    record CannotRemoveLeader() implements RemoveMemberOutcome {}
    record NotLeader() implements RemoveMemberOutcome {}
}
