package com.zornus.guilds.proxy.storage;

public sealed interface CreateGuildOutcome permits
        CreateGuildOutcome.Created,
        CreateGuildOutcome.AlreadyInGuild {
    record Created() implements CreateGuildOutcome {}
    record AlreadyInGuild() implements CreateGuildOutcome {}
}
