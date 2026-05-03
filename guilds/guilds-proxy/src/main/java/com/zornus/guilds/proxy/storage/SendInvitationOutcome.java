package com.zornus.guilds.proxy.storage;

public sealed interface SendInvitationOutcome permits
        SendInvitationOutcome.Sent,
        SendInvitationOutcome.TargetAlreadyInGuild,
        SendInvitationOutcome.TargetInAnotherGuild,
        SendInvitationOutcome.GuildFull,
        SendInvitationOutcome.CooldownActive,
        SendInvitationOutcome.SenderLimitReached,
        SendInvitationOutcome.ReceiverLimitReached,
        SendInvitationOutcome.InvitesDisabled,
        SendInvitationOutcome.AlreadyInvited,
        SendInvitationOutcome.SenderNoLongerLeader,
        SendInvitationOutcome.GuildNoLongerExists {
    record Sent() implements SendInvitationOutcome {}
    record TargetAlreadyInGuild() implements SendInvitationOutcome {}
    record TargetInAnotherGuild() implements SendInvitationOutcome {}
    record GuildFull() implements SendInvitationOutcome {}
    record CooldownActive() implements SendInvitationOutcome {}
    record SenderLimitReached() implements SendInvitationOutcome {}
    record ReceiverLimitReached() implements SendInvitationOutcome {}
    record InvitesDisabled(String privacy) implements SendInvitationOutcome {}
    record AlreadyInvited() implements SendInvitationOutcome {}
    record SenderNoLongerLeader() implements SendInvitationOutcome {}
    record GuildNoLongerExists() implements SendInvitationOutcome {}
}
