package com.zornus.parties.proxy.model;

import com.zornus.parties.proxy.PartyProxyConstants;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.*;

public record Party(
        @NonNull UUID partyId,
        @NonNull String partyName,
        @NonNull UUID leaderId,
        @NonNull String leaderName,
        @NonNull Map<UUID, String> memberNames,
        @NonNull Optional<Instant> lastWarpTime
) {

    public Party {
        memberNames = Map.copyOf(memberNames);
    }

    public Party(@NonNull UUID leaderId, @NonNull String leaderName) {
        this(UUID.randomUUID(), leaderName + "'s Party", leaderId, leaderName,
                Map.of(leaderId, leaderName), Optional.empty());
    }

    public boolean isLeader(@NonNull UUID playerId) {
        return leaderId.equals(playerId);
    }

    public boolean isMember(@NonNull UUID playerId) {
        return memberNames.containsKey(playerId);
    }

    public @NonNull Set<UUID> getMemberIds() {
        return memberNames.keySet();
    }

    public boolean isFull() {
        return memberNames.size() >= PartyProxyConstants.MAX_PARTY_SIZE;
    }

    public @NonNull List<UUID> getNonLeaderMembers() {
        List<UUID> nonLeaders = new ArrayList<>();
        for (UUID memberId : memberNames.keySet()) {
            if (!memberId.equals(leaderId)) {
                nonLeaders.add(memberId);
            }
        }
        return nonLeaders;
    }
}
