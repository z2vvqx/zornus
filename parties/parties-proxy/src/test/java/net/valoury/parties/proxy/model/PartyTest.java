package net.valoury.parties.proxy.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTest {

    private static final UUID LEADER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MODERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void leaderAndModeratorCanManageMembers() {
        Party party = party();

        assertTrue(party.canManageMembers(LEADER_ID));
        assertTrue(party.canManageMembers(MODERATOR_ID));
        assertFalse(party.canManageMembers(MEMBER_ID));
    }

    @Test
    void moderatorCanOnlyKickRegularMembers() {
        Party party = party();

        assertTrue(party.canKick(MODERATOR_ID, MEMBER_ID));
        assertFalse(party.canKick(MODERATOR_ID, LEADER_ID));
        assertFalse(party.canKick(MODERATOR_ID, MODERATOR_ID));
    }

    @Test
    void leaderCanKickModeratorsButNotSelf() {
        Party party = party();

        assertTrue(party.canKick(LEADER_ID, MODERATOR_ID));
        assertFalse(party.canKick(LEADER_ID, LEADER_ID));
    }

    @Test
    void rejectsModeratorOutsideParty() {
        UUID outsiderId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        assertThrows(IllegalArgumentException.class, () -> new Party(
                UUID.randomUUID(),
                LEADER_ID,
                Set.of(LEADER_ID, MEMBER_ID),
                Set.of(outsiderId),
                Optional.of(Instant.EPOCH)
        ));
    }

    @Test
    void rejectsSingleMemberParty() {
        assertThrows(IllegalArgumentException.class, () -> new Party(
                UUID.randomUUID(),
                LEADER_ID,
                Set.of(LEADER_ID),
                Set.of(),
                Optional.empty()
        ));
    }

    private static Party party() {
        return new Party(
                UUID.randomUUID(),
                LEADER_ID,
                Set.of(LEADER_ID, MODERATOR_ID, MEMBER_ID),
                Set.of(MODERATOR_ID),
                Optional.empty()
        );
    }
}
