package net.valoury.guilds.proxy.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuildRankTest {

    @Test
    void usesTheExactSkriptRankNamesInHierarchyOrder() {
        assertEquals(
                List.of("Leader", "Director", "Officer", "Associate", "Outcast"),
                GuildRank.highestFirst().stream()
                        .map(GuildRank::displayName)
                        .toList()
        );
    }

    @Test
    void usesRankInitialsForGuildChatExceptForOutcasts() {
        assertEquals("L", GuildRank.LEADER.chatTagInitial().orElseThrow());
        assertEquals("D", GuildRank.DIRECTOR.chatTagInitial().orElseThrow());
        assertEquals("O", GuildRank.OFFICER.chatTagInitial().orElseThrow());
        assertEquals("A", GuildRank.ASSOCIATE.chatTagInitial().orElseThrow());
        assertTrue(GuildRank.OUTCAST.chatTagInitial().isEmpty());
    }

    @Test
    void grantsInvitationCapabilityToAssociatesAndHigher() {
        assertTrue(GuildRank.LEADER.canManageInvitations());
        assertTrue(GuildRank.DIRECTOR.canManageInvitations());
        assertTrue(GuildRank.OFFICER.canManageInvitations());
        assertTrue(GuildRank.ASSOCIATE.canManageInvitations());
        assertFalse(GuildRank.OUTCAST.canManageInvitations());
    }

    @Test
    void allowsOfficersAndHigherToKickOnlyLowerRanks() {
        assertTrue(GuildRank.OFFICER.canKick(GuildRank.ASSOCIATE));
        assertTrue(GuildRank.DIRECTOR.canKick(GuildRank.OFFICER));
        assertTrue(GuildRank.LEADER.canKick(GuildRank.DIRECTOR));

        assertFalse(GuildRank.OFFICER.canKick(GuildRank.OFFICER));
        assertFalse(GuildRank.DIRECTOR.canKick(GuildRank.DIRECTOR));
        assertFalse(GuildRank.ASSOCIATE.canKick(GuildRank.OUTCAST));
    }

    @Test
    void neverAllowsPromotionToTheActorsOwnRank() {
        assertFalse(GuildRank.LEADER.canPromote(GuildRank.DIRECTOR));
        assertFalse(GuildRank.DIRECTOR.canPromote(GuildRank.OFFICER));

        assertTrue(GuildRank.LEADER.canPromote(GuildRank.OFFICER));
        assertTrue(GuildRank.DIRECTOR.canPromote(GuildRank.ASSOCIATE));
    }

    @Test
    void allowsDirectorsAndLeadersToDemoteOnlyLowerRanks() {
        assertTrue(GuildRank.LEADER.canDemote(GuildRank.DIRECTOR));
        assertTrue(GuildRank.DIRECTOR.canDemote(GuildRank.OFFICER));

        assertFalse(GuildRank.DIRECTOR.canDemote(GuildRank.DIRECTOR));
        assertFalse(GuildRank.OFFICER.canDemote(GuildRank.ASSOCIATE));
        assertFalse(GuildRank.LEADER.canDemote(GuildRank.OUTCAST));
    }

    @Test
    void allowsOnlyLeadersToUpdateGuildColor() {
        assertTrue(GuildRank.LEADER.canUpdateColor());
        assertFalse(GuildRank.DIRECTOR.canUpdateColor());
        assertFalse(GuildRank.OFFICER.canUpdateColor());
    }

    @Test
    void rejectsUnknownStoredRanks() {
        assertEquals(GuildRank.ASSOCIATE, GuildRank.fromStoredName("Associate"));
        assertThrows(
                IllegalArgumentException.class,
                () -> GuildRank.fromStoredName("associate")
        );
    }
}
