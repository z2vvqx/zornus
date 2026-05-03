package com.zornus.guilds.proxy.model;

import com.zornus.guilds.proxy.GuildProxyConstants;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.*;

public record Guild(
        @NonNull UUID guildId,
        @NonNull String guildName,
        @NonNull String guildTag,
        @NonNull String guildColor,
        @NonNull UUID leaderId,
        @NonNull Instant createdAt,
        @NonNull Set<UUID> memberIds
) {

    public Guild {
        memberIds = Set.copyOf(memberIds);
    }

    public Guild(@NonNull UUID leaderId, @NonNull String guildName, @NonNull String guildTag, @NonNull String guildColor) {
        this(UUID.randomUUID(), guildName, guildTag, guildColor, leaderId, Instant.now(), Set.of(leaderId));
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
        return memberIds.size() >= GuildProxyConstants.MAX_GUILD_SIZE;
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
