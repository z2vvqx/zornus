package net.valoury.parties.proxy.storage;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface SendInvitationOutcome permits
        SendInvitationOutcome.Sent,
        SendInvitationOutcome.TargetAlreadyInParty,
        SendInvitationOutcome.PartyFull,
        SendInvitationOutcome.CooldownActive,
        SendInvitationOutcome.SenderLimitReached,
        SendInvitationOutcome.ReceiverLimitReached,
        SendInvitationOutcome.InvitesDisabled,
        SendInvitationOutcome.AlreadyInvited,
        SendInvitationOutcome.SenderInsufficientRole,
        SendInvitationOutcome.PartyNoLongerExists {
    record Sent(@NonNull Optional<UUID> partyId) implements SendInvitationOutcome {
        public Sent {
            partyId = Objects.requireNonNull(partyId, "partyId");
        }
    }

    record TargetAlreadyInParty() implements SendInvitationOutcome {
    }

    record PartyFull() implements SendInvitationOutcome {
    }

    record CooldownActive() implements SendInvitationOutcome {
    }

    record SenderLimitReached() implements SendInvitationOutcome {
    }

    record ReceiverLimitReached() implements SendInvitationOutcome {
    }

    record InvitesDisabled(String privacy) implements SendInvitationOutcome {
    }

    record AlreadyInvited() implements SendInvitationOutcome {
    }

    record SenderInsufficientRole() implements SendInvitationOutcome {
    }

    record PartyNoLongerExists() implements SendInvitationOutcome {
    }
}
