package com.zornus.guilds.proxy.storage;

public sealed interface CreateGuildOutcome permits
        CreateGuildOutcome.Created,
        CreateGuildOutcome.AlreadyInGuild,
        CreateGuildOutcome.GuildNameAlreadyExists {
    record Created() implements CreateGuildOutcome {}
    record AlreadyInGuild() implements CreateGuildOutcome {}
    record GuildNameAlreadyExists() implements CreateGuildOutcome {}
}
