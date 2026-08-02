package net.valoury.parties.proxy.storage;

public sealed interface RevokePartyInvitationOutcome {
    record Revoked() implements RevokePartyInvitationOutcome {
    }

    record PartyNotFound() implements RevokePartyInvitationOutcome {
    }

    record InsufficientRole() implements RevokePartyInvitationOutcome {
    }

    record InvitationNotFound() implements RevokePartyInvitationOutcome {
    }
}
