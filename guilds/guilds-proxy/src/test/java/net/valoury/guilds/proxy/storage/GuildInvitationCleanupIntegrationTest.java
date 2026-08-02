package net.valoury.guilds.proxy.storage;

import net.valoury.guilds.proxy.GuildProxyConstants;
import net.valoury.guilds.proxy.model.GuildRankChangeDirection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseUrlConfigured")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class GuildInvitationCleanupIntegrationTest {

    private static final String DATABASE_URL_PROPERTY = "guilds.storage.integration.url";

    private GuildPostgresStorage storage;

    private static boolean databaseUrlConfigured() {
        return System.getProperty(DATABASE_URL_PROPERTY) != null;
    }

    @BeforeAll
    void initializeStorage() {
        storage = new GuildPostgresStorage(
                System.getProperty(DATABASE_URL_PROPERTY),
                GuildProxyConstants.POSTGRESQL_USER,
                GuildProxyConstants.POSTGRESQL_PASSWORD
        );
    }

    @AfterAll
    void closeStorage() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void removesOutgoingInvitationsWhenMemberLeaves() {
        verifyOutgoingInvitationsAreRemoved(false);
    }

    @Test
    void removesOutgoingInvitationsWhenMemberIsKicked() {
        verifyOutgoingInvitationsAreRemoved(true);
    }

    private void verifyOutgoingInvitationsAreRemoved(boolean kicked) {
        UUID leaderId = UUID.randomUUID();
        UUID invitingMemberId = UUID.randomUUID();
        UUID invitedTargetId = UUID.randomUUID();
        String uniqueIdentifier = UUID.randomUUID().toString().replace("-", "");
        UUID guildId = createGuildWithInvitingMember(
                leaderId,
                invitingMemberId,
                uniqueIdentifier
        );

        try {
            assertInstanceOf(
                    SendInvitationOutcome.Sent.class,
                    storage.trySendInvitation(
                            guildId,
                            invitingMemberId,
                            invitedTargetId,
                            true
                    ).join()
            );

            UUID requesterId = kicked ? leaderId : invitingMemberId;
            assertInstanceOf(
                    RemoveMemberOutcome.MemberRemoved.class,
                    storage.tryRemoveMember(
                            guildId,
                            invitingMemberId,
                            requesterId
                    ).join()
            );

            assertTrue(storage.fetchOutgoingInvitations(invitingMemberId).join().isEmpty());
            assertInstanceOf(
                    AcceptInvitationOutcome.InvitationNoLongerValid.class,
                    storage.tryAcceptInvitation(
                            guildId,
                            invitingMemberId,
                            invitedTargetId
                    ).join()
            );
        } finally {
            storage.tryDisbandGuild(guildId, leaderId).join();
        }
    }

    private UUID createGuildWithInvitingMember(
            UUID leaderId,
            UUID invitingMemberId,
            String uniqueIdentifier
    ) {
        String guildName = "Guild" + uniqueIdentifier.substring(0, 12);
        String guildTag = uniqueIdentifier.substring(0, 5);
        assertInstanceOf(
                CreateGuildOutcome.Created.class,
                storage.tryCreateGuild(
                        leaderId,
                        guildName,
                        guildTag,
                        "<white>"
                ).join()
        );

        UUID guildId = storage.getPlayerGuild(leaderId).join().orElseThrow().guildId();
        assertInstanceOf(
                SendInvitationOutcome.Sent.class,
                storage.trySendInvitation(
                        guildId,
                        leaderId,
                        invitingMemberId,
                        true
                ).join()
        );
        assertInstanceOf(
                AcceptInvitationOutcome.Accepted.class,
                storage.tryAcceptInvitation(
                        guildId,
                        leaderId,
                        invitingMemberId
                ).join()
        );
        assertInstanceOf(
                GuildRankChangeOutcome.Changed.class,
                storage.tryChangeMemberRank(
                        guildId,
                        leaderId,
                        invitingMemberId,
                        GuildRankChangeDirection.PROMOTION
                ).join()
        );
        return guildId;
    }
}
