package com.zornus.parties.proxy.model;

import com.zornus.parties.proxy.PartyProxyConstants;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.*;

public record Party(
        @NonNull UUID partyId,
        @NonNull UUID leaderId,
        @NonNull Set<UUID> memberIds,
        @NonNull Optional<Instant> lastWarpTime
) {

    public Party {
        memberIds = Set.copyOf(memberIds);
    }

    public Party(@NonNull UUID leaderId) {
        this(UUID.randomUUID(), leaderId, Set.of(leaderId), Optional.empty());
    }

    public boolean isLeader(@NonNull UUID playerId) {
        return leaderId.equals(playerId);
    }

    public boolean isMember(@NonNull UUID playerId) {
        return memberIds.contains(playerId);
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
