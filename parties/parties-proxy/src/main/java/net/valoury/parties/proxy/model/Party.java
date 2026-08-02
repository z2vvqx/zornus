package net.valoury.parties.proxy.model;

import net.valoury.parties.proxy.PartyProxyConstants;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.*;

public record Party(
        @NonNull UUID partyId,
        @NonNull UUID leaderId,
        @NonNull Set<UUID> memberIds,
        @NonNull Set<UUID> moderatorIds,
        @NonNull Optional<Instant> lastWarpTime
) {

    public Party {
        memberIds = Set.copyOf(memberIds);
        moderatorIds = Set.copyOf(moderatorIds);
        if (!memberIds.contains(leaderId)) {
            throw new IllegalArgumentException("Party leader must be a party member");
        }
        if (memberIds.size() < 2) {
            throw new IllegalArgumentException("A party must have at least two members");
        }
        if (!memberIds.containsAll(moderatorIds) || moderatorIds.contains(leaderId)) {
            throw new IllegalArgumentException("Party moderators must be non-leader party members");
        }
    }

    public boolean isLeader(@NonNull UUID playerId) {
        return leaderId.equals(playerId);
    }

    public boolean isMember(@NonNull UUID playerId) {
        return memberIds.contains(playerId);
    }

    public boolean isModerator(@NonNull UUID playerId) {
        return moderatorIds.contains(playerId);
    }

    public boolean canManageMembers(@NonNull UUID playerId) {
        return isLeader(playerId) || isModerator(playerId);
    }

    public boolean canKick(@NonNull UUID actorId, @NonNull UUID targetId) {
        if (actorId.equals(targetId) || isLeader(targetId)) {
            return false;
        }
        if (isLeader(actorId)) {
            return isMember(targetId);
        }
        return isModerator(actorId) && isMember(targetId) && !isModerator(targetId);
    }

    public @NonNull Set<UUID> getMemberIds() {
        return memberIds;
    }

    public boolean isFull() {
        return memberIds.size() >= PartyProxyConstants.MAX_PARTY_SIZE;
    }

    public @NonNull List<UUID> getNonLeaderMembers() {
        List<UUID> nonLeaders = new ArrayList<>();
        for (UUID memberId : memberIds) {
            if (!memberId.equals(leaderId)) {
                nonLeaders.add(memberId);
            }
        }
        return nonLeaders;
    }
}
