package com.zornus.guilds.proxy.storage;

public sealed interface RenameGuildOutcome permits
        RenameGuildOutcome.Renamed,
        RenameGuildOutcome.GuildNotFound,
        RenameGuildOutcome.NotLeader,
        RenameGuildOutcome.NameAlreadyExists {
    record Renamed() implements RenameGuildOutcome {}
    record GuildNotFound() implements RenameGuildOutcome {}
    record NotLeader() implements RenameGuildOutcome {}
    record NameAlreadyExists() implements RenameGuildOutcome {}
}
