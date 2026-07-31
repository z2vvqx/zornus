package net.valoury.guilds.proxy.model.result;

import net.valoury.guilds.proxy.model.GuildResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GuildCanonicalTargetResultTest {

    @Test
    void preservesCanonicalNameForLeadershipTransferMessages() {
        GuildResults.TransferLeadership confirmation =
                GuildResults.TransferLeadership.from(
                        GuildResult.TRANSFER_CONFIRMATION_REQUIRED,
                        "MMAJED"
                );
        GuildResults.TransferLeadership transferred =
                GuildResults.TransferLeadership.from(
                        GuildResult.LEADERSHIP_TRANSFERRED,
                        "MMAJED"
                );

        assertEquals(
                "MMAJED",
                assertInstanceOf(
                        GuildResults.TransferLeadership.ConfirmationRequired.class,
                        confirmation
                ).targetName()
        );
        assertEquals(
                "MMAJED",
                assertInstanceOf(
                        GuildResults.TransferLeadership.Transferred.class,
                        transferred
                ).targetName()
        );
    }

    @Test
    void preservesCanonicalNameForKickAndRevokeMessages() {
        GuildResults.KickMember kicked = GuildResults.KickMember.from(
                GuildResult.MEMBER_REMOVED,
                "MMAJED"
        );
        GuildResults.RevokeInvitation revoked = GuildResults.RevokeInvitation.from(
                GuildResult.INVITATION_REVOKED,
                "MMAJED"
        );

        assertEquals(
                "MMAJED",
                assertInstanceOf(GuildResults.KickMember.Removed.class, kicked).targetName()
        );
        assertEquals(
                "MMAJED",
                assertInstanceOf(
                        GuildResults.RevokeInvitation.Revoked.class,
                        revoked
                ).targetName()
        );
    }
}
