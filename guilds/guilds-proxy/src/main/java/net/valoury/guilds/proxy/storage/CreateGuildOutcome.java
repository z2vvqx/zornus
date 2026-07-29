package net.valoury.guilds.proxy.storage;

public sealed interface CreateGuildOutcome permits
        CreateGuildOutcome.Created,
        CreateGuildOutcome.AlreadyInGuild,
        CreateGuildOutcome.GuildNameAlreadyExists,
        CreateGuildOutcome.GuildTagAlreadyExists {
    record Created() implements CreateGuildOutcome {
    }

    record AlreadyInGuild() implements CreateGuildOutcome {
    }

    record GuildNameAlreadyExists() implements CreateGuildOutcome {
    }

    record GuildTagAlreadyExists() implements CreateGuildOutcome {
    }
}
