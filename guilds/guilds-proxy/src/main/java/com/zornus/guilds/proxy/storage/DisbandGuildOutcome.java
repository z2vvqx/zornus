package com.zornus.guilds.proxy.storage;

public sealed interface DisbandGuildOutcome permits
        DisbandGuildOutcome.Disbanded,
        DisbandGuildOutcome.GuildNotFound,
        DisbandGuildOutcome.NotLeader {
    record Disbanded() implements DisbandGuildOutcome {}
    record GuildNotFound() implements DisbandGuildOutcome {}
    record NotLeader() implements DisbandGuildOutcome {}
}
