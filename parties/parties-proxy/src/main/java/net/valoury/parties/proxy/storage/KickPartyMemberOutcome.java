package net.valoury.parties.proxy.storage;

public sealed interface KickPartyMemberOutcome {
    record Kicked() implements KickPartyMemberOutcome {
    }

    record PartyNotFound() implements KickPartyMemberOutcome {
    }

    record InsufficientRole() implements KickPartyMemberOutcome {
    }

    record MemberNotFound() implements KickPartyMemberOutcome {
    }

    record CannotKickSelf() implements KickPartyMemberOutcome {
    }

    record CannotKickLeader() implements KickPartyMemberOutcome {
    }

    record CannotKickModerator() implements KickPartyMemberOutcome {
    }
}
