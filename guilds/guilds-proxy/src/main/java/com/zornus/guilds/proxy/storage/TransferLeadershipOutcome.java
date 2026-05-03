package com.zornus.guilds.proxy.storage;

public sealed interface TransferLeadershipOutcome permits
        TransferLeadershipOutcome.Transferred,
        TransferLeadershipOutcome.GuildNotFound,
        TransferLeadershipOutcome.TargetNotMember {
    record Transferred() implements TransferLeadershipOutcome {}
    record GuildNotFound() implements TransferLeadershipOutcome {}
    record TargetNotMember() implements TransferLeadershipOutcome {}
}
