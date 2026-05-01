package com.zornus.parties.proxy.storage;

public sealed interface SendInvitationOutcome {
    record Sent() implements SendInvitationOutcome {}
    record TargetAlreadyInParty() implements SendInvitationOutcome {}
    record PartyFull() implements SendInvitationOutcome {}
    record CooldownActive() implements SendInvitationOutcome {}
    record SenderLimitReached() implements SendInvitationOutcome {}
    record ReceiverLimitReached() implements SendInvitationOutcome {}
    record InvitesDisabled(String privacy) implements SendInvitationOutcome {}
    record AlreadyInvited() implements SendInvitationOutcome {}
}
