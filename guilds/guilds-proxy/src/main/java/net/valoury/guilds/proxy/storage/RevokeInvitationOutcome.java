package net.valoury.guilds.proxy.storage;

public sealed interface RevokeInvitationOutcome {
    record Revoked() implements RevokeInvitationOutcome {
    }

    record InvitationNotFound() implements RevokeInvitationOutcome {
    }

    record InsufficientRank() implements RevokeInvitationOutcome {
    }

    record GuildNotFound() implements RevokeInvitationOutcome {
    }
}
