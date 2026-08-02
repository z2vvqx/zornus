package net.valoury.guilds.proxy.storage;

public sealed interface UpdateGuildJoinPolicyOutcome {
    record Updated() implements UpdateGuildJoinPolicyOutcome {
    }

    record InsufficientRank() implements UpdateGuildJoinPolicyOutcome {
    }

    record GuildNotFound() implements UpdateGuildJoinPolicyOutcome {
    }
}
