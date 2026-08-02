package net.valoury.parties.proxy.storage;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public sealed interface JoinOutcome permits
        JoinOutcome.Joined,
        JoinOutcome.PartyFull,
        JoinOutcome.AlreadyMember,
        JoinOutcome.InvitationExpired,
        JoinOutcome.InvitationNoLongerValid {
    record Joined(@NonNull UUID partyId) implements JoinOutcome {
    }

    record PartyFull() implements JoinOutcome {
    }

    record AlreadyMember() implements JoinOutcome {
    }

    record InvitationExpired() implements JoinOutcome {
    }

    record InvitationNoLongerValid() implements JoinOutcome {
    }
}
