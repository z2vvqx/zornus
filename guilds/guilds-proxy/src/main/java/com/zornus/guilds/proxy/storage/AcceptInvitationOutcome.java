package com.zornus.guilds.proxy.storage;

public sealed interface AcceptInvitationOutcome permits
        AcceptInvitationOutcome.Accepted,
        AcceptInvitationOutcome.GuildFull,
        AcceptInvitationOutcome.AlreadyInGuild,
        AcceptInvitationOutcome.InvitationExpired,
        AcceptInvitationOutcome.InvitationNoLongerValid {
    record Accepted() implements AcceptInvitationOutcome {}
    record GuildFull() implements AcceptInvitationOutcome {}
    record AlreadyInGuild() implements AcceptInvitationOutcome {}
    record InvitationExpired() implements AcceptInvitationOutcome {}
    record InvitationNoLongerValid() implements AcceptInvitationOutcome {}
}
