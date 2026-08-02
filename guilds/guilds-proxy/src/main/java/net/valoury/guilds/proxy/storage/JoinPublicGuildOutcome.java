package net.valoury.guilds.proxy.storage;

public sealed interface JoinPublicGuildOutcome {
    record Joined() implements JoinPublicGuildOutcome {
    }

    record AlreadyInGuild() implements JoinPublicGuildOutcome {
    }

    record GuildFull() implements JoinPublicGuildOutcome {
    }

    record GuildPrivate() implements JoinPublicGuildOutcome {
    }

    record GuildNotFound() implements JoinPublicGuildOutcome {
    }
}
