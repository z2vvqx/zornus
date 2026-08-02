package net.valoury.parties.proxy.storage;

import net.valoury.parties.proxy.PartyProxyConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseUrlConfigured")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class PartyInvitationCleanupIntegrationTest {

    private static final String DATABASE_URL_PROPERTY = "parties.storage.integration.url";

    private PartyPostgresStorage storage;

    private static boolean databaseUrlConfigured() {
        return System.getProperty(DATABASE_URL_PROPERTY) != null;
    }

    @BeforeAll
    void initializeStorage() {
        storage = new PartyPostgresStorage(
                System.getProperty(DATABASE_URL_PROPERTY),
                PartyProxyConstants.POSTGRESQL_USER,
                PartyProxyConstants.POSTGRESQL_PASSWORD
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
        UUID remainingMemberId = UUID.randomUUID();
        UUID invitedTargetId = UUID.randomUUID();

        UUID partyId = createParty(leaderId, invitingMemberId, remainingMemberId);
        try {
            assertInstanceOf(
                    PartyModeratorChangeOutcome.Changed.class,
                    storage.updateModeratorStatus(
                            partyId,
                            leaderId,
                            invitingMemberId,
                            true
                    ).join()
            );
            assertInstanceOf(
                    SendInvitationOutcome.Sent.class,
                    storage.trySendInvitation(
                            Optional.of(partyId),
                            invitingMemberId,
                            invitedTargetId,
                            true
                    ).join()
            );

            if (kicked) {
                assertInstanceOf(
                        KickPartyMemberOutcome.Kicked.class,
                        storage.tryKickMember(
                                partyId,
                                leaderId,
                                invitingMemberId
                        ).join()
                );
            } else {
                assertInstanceOf(
                        RemoveMemberOutcome.MemberRemoved.class,
                        storage.removeMember(partyId, invitingMemberId).join()
                );
            }

            assertTrue(storage.fetchOutgoingInvitations(invitingMemberId).join().isEmpty());
            assertTrue(storage.findInvitationFromSender(
                    invitedTargetId,
                    invitingMemberId
            ).join().isEmpty());
            assertInstanceOf(
                    JoinOutcome.InvitationNoLongerValid.class,
                    storage.acceptInvitationAndJoin(
                            invitedTargetId,
                            invitingMemberId
                    ).join()
            );
        } finally {
            storage.disbandParty(partyId, leaderId).join();
        }
    }

    private UUID createParty(
            UUID leaderId,
            UUID invitingMemberId,
            UUID remainingMemberId
    ) {
        storage.trySendInvitation(
                Optional.empty(),
                leaderId,
                invitingMemberId,
                true
        ).join();
        storage.trySendInvitation(
                Optional.empty(),
                leaderId,
                remainingMemberId,
                true
        ).join();

        JoinOutcome.Joined createdParty = assertInstanceOf(
                JoinOutcome.Joined.class,
                storage.acceptInvitationAndJoin(invitingMemberId, leaderId).join()
        );
        assertInstanceOf(
                JoinOutcome.Joined.class,
                storage.acceptInvitationAndJoin(remainingMemberId, leaderId).join()
        );
        return createdParty.partyId();
    }
}
