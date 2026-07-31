package net.valoury.guilds.proxy.model;

import net.valoury.guilds.proxy.GuildProxyConstants;
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
        @NonNull Map<UUID, GuildRank> memberRanks
) {

    public Guild {
        memberRanks = Map.copyOf(memberRanks);
        long leaderRankCount = memberRanks.values().stream()
                .filter(rank -> rank == GuildRank.LEADER)
                .count();
        if (memberRanks.get(leaderId) != GuildRank.LEADER || leaderRankCount != 1) {
            throw new IllegalArgumentException(
                    "Guild must have exactly one Leader matching its leader identifier");
        }
    }

    public Guild(@NonNull UUID leaderId, @NonNull String guildName, @NonNull String guildTag, @NonNull String guildColor) {
        this(UUID.randomUUID(), guildName, guildTag, guildColor, leaderId, Instant.now(),
                Map.of(leaderId, GuildRank.LEADER));
    }

    public boolean isLeader(@NonNull UUID playerId) {
        return leaderId.equals(playerId);
    }

    public boolean isMember(@NonNull UUID playerId) {
        return memberRanks.containsKey(playerId);
    }

    public @NonNull Set<UUID> getMemberIds() {
        return memberRanks.keySet();
    }

    public @NonNull Optional<GuildRank> findMemberRank(@NonNull UUID playerId) {
        return Optional.ofNullable(memberRanks.get(playerId));
    }

    public boolean isFull() {
        return memberRanks.size() >= GuildProxyConstants.MAX_GUILD_SIZE;
    }

    public @NonNull List<UUID> getNonLeaderMembers() {
        List<UUID> nonLeaders = new ArrayList<>();
        for (UUID memberId : memberRanks.keySet()) {
            if (!memberId.equals(leaderId)) {
                nonLeaders.add(memberId);
            }
        }
        return nonLeaders;
    }
}
