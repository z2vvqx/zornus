package net.valoury.parties.proxy.storage;

import net.valoury.parties.proxy.PartyProxyConstants;
import net.valoury.parties.proxy.model.Party;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseUrlConfigured")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class PartyPostgresStorageIntegrationTest {

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
    void createsAndDisbandsPartiesThroughTheInvitationLifecycle() {
        UUID leaderId = UUID.randomUUID();
        UUID secondMemberId = UUID.randomUUID();
        UUID thirdMemberId = UUID.randomUUID();
        UUID fourthMemberId = UUID.randomUUID();

        SendInvitationOutcome.Sent standaloneInvitation = assertInstanceOf(
                SendInvitationOutcome.Sent.class,
                storage.trySendInvitation(
                        Optional.empty(),
                        leaderId,
                        secondMemberId,
                        true
                ).join()
        );
        assertTrue(standaloneInvitation.partyId().isEmpty());

        SendInvitationOutcome.Sent secondStandaloneInvitation = assertInstanceOf(
                SendInvitationOutcome.Sent.class,
                storage.trySendInvitation(
                        Optional.empty(),
                        leaderId,
                        thirdMemberId,
                        true
                ).join()
        );
        assertTrue(secondStandaloneInvitation.partyId().isEmpty());

        CompletableFuture<JoinOutcome> secondMemberAcceptance =
                storage.acceptInvitationAndJoin(secondMemberId, leaderId);
        CompletableFuture<JoinOutcome> thirdMemberAcceptance =
                storage.acceptInvitationAndJoin(thirdMemberId, leaderId);
        CompletableFuture.allOf(secondMemberAcceptance, thirdMemberAcceptance).join();

        JoinOutcome.Joined createdParty = assertInstanceOf(
                JoinOutcome.Joined.class,
                secondMemberAcceptance.join()
        );
        JoinOutcome.Joined joinedCreatedParty = assertInstanceOf(
                JoinOutcome.Joined.class,
                thirdMemberAcceptance.join()
        );
        assertEquals(createdParty.partyId(), joinedCreatedParty.partyId());
        Party threeMemberParty = storage.fetchParty(createdParty.partyId()).join().orElseThrow();
        assertEquals(leaderId, threeMemberParty.leaderId());
        assertEquals(
                Set.of(leaderId, secondMemberId, thirdMemberId),
                threeMemberParty.memberIds()
        );

        SendInvitationOutcome.Sent partyInvitation = assertInstanceOf(
                SendInvitationOutcome.Sent.class,
                storage.trySendInvitation(
                        Optional.of(createdParty.partyId()),
                        leaderId,
                        fourthMemberId,
                        true
                ).join()
        );
        assertEquals(Optional.of(createdParty.partyId()), partyInvitation.partyId());

        JoinOutcome.Joined joinedParty = assertInstanceOf(
                JoinOutcome.Joined.class,
                storage.acceptInvitationAndJoin(fourthMemberId, leaderId).join()
        );
        assertEquals(createdParty.partyId(), joinedParty.partyId());
        assertEquals(
                Set.of(leaderId, secondMemberId, thirdMemberId, fourthMemberId),
                storage.fetchParty(createdParty.partyId()).join().orElseThrow().memberIds()
        );

        assertInstanceOf(
                KickPartyMemberOutcome.Kicked.class,
                storage.tryKickMember(createdParty.partyId(), leaderId, fourthMemberId).join()
        );
        assertEquals(
                Set.of(leaderId, secondMemberId, thirdMemberId),
                storage.fetchParty(createdParty.partyId()).join().orElseThrow().memberIds()
        );

        assertInstanceOf(
                KickPartyMemberOutcome.Kicked.class,
                storage.tryKickMember(createdParty.partyId(), leaderId, thirdMemberId).join()
        );
        assertEquals(
                Set.of(leaderId, secondMemberId),
                storage.fetchParty(createdParty.partyId()).join().orElseThrow().memberIds()
        );

        assertInstanceOf(
                KickPartyMemberOutcome.Kicked.class,
                storage.tryKickMember(createdParty.partyId(), leaderId, secondMemberId).join()
        );
        assertTrue(storage.fetchParty(createdParty.partyId()).join().isEmpty());

        UUID leavingLeaderId = UUID.randomUUID();
        UUID remainingMemberId = UUID.randomUUID();
        storage.trySendInvitation(
                Optional.empty(),
                leavingLeaderId,
                remainingMemberId,
                true
        ).join();
        JoinOutcome.Joined partyForLeave = assertInstanceOf(
                JoinOutcome.Joined.class,
                storage.acceptInvitationAndJoin(remainingMemberId, leavingLeaderId).join()
        );
        assertInstanceOf(
                RemoveMemberOutcome.PartyDisbanded.class,
                storage.removeMember(partyForLeave.partyId(), leavingLeaderId).join()
        );
        assertTrue(storage.fetchParty(partyForLeave.partyId()).join().isEmpty());
    }
}
