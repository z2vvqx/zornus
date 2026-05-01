package com.zornus.parties.proxy.storage;

public sealed interface SendInvitationOutcome permits
        SendInvitationOutcome.Sent,
        SendInvitationOutcome.TargetAlreadyInParty,
        SendInvitationOutcome.PartyFull,
        SendInvitationOutcome.CooldownActive,
        SendInvitationOutcome.SenderLimitReached,
        SendInvitationOutcome.ReceiverLimitReached,
        SendInvitationOutcome.InvitesDisabled,
        SendInvitationOutcome.AlreadyInvited,
        SendInvitationOutcome.SenderNoLongerLeader,
        SendInvitationOutcome.PartyNoLongerExists {
    record Sent() implements SendInvitationOutcome {}
    record TargetAlreadyInParty() implements SendInvitationOutcome {}
    record PartyFull() implements SendInvitationOutcome {}
    record CooldownActive() implements SendInvitationOutcome {}
    record SenderLimitReached() implements SendInvitationOutcome {}
    record ReceiverLimitReached() implements SendInvitationOutcome {}
    record InvitesDisabled(String privacy) implements SendInvitationOutcome {}
    record AlreadyInvited() implements SendInvitationOutcome {}
    record SenderNoLongerLeader() implements SendInvitationOutcome {}
    record PartyNoLongerExists() implements SendInvitationOutcome {}
}
