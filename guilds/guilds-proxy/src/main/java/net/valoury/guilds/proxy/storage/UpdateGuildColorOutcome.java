package net.valoury.guilds.proxy.storage;

public sealed interface UpdateGuildColorOutcome {
    record Updated() implements UpdateGuildColorOutcome {
    }

    record InsufficientRank() implements UpdateGuildColorOutcome {
    }

    record GuildNotFound() implements UpdateGuildColorOutcome {
    }
}
