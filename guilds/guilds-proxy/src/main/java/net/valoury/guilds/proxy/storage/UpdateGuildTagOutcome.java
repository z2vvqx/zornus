package net.valoury.guilds.proxy.storage;

public sealed interface UpdateGuildTagOutcome permits
        UpdateGuildTagOutcome.Updated,
        UpdateGuildTagOutcome.GuildNotFound,
        UpdateGuildTagOutcome.NotLeader,
        UpdateGuildTagOutcome.GuildTagAlreadyExists {
    record Updated() implements UpdateGuildTagOutcome {
    }

    record GuildNotFound() implements UpdateGuildTagOutcome {
    }

    record NotLeader() implements UpdateGuildTagOutcome {
    }

    record GuildTagAlreadyExists() implements UpdateGuildTagOutcome {
    }
}
