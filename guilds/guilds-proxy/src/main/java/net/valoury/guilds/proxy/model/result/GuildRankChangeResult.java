package net.valoury.guilds.proxy.model.result;

import net.valoury.guilds.proxy.model.GuildRank;
import org.jspecify.annotations.NonNull;

public sealed interface GuildRankChangeResult {
    record Changed(
            @NonNull String targetName,
            @NonNull String actorName,
            @NonNull GuildRank previousRank,
            @NonNull GuildRank newRank
    ) implements GuildRankChangeResult {
    }

    record NotInGuild() implements GuildRankChangeResult {
    }

    record PlayerNotFound() implements GuildRankChangeResult {
    }

    record PlayerNotInGuild(@NonNull String targetName) implements GuildRankChangeResult {
    }

    record CannotChangeOwnRank() implements GuildRankChangeResult {
    }

    record InsufficientRank() implements GuildRankChangeResult {
    }

    record CannotManageRank(@NonNull String targetName) implements GuildRankChangeResult {
    }

    record PromotionWouldMatchActorRank(@NonNull String targetName) implements GuildRankChangeResult {
    }

    record AlreadyHighestRank(@NonNull String targetName) implements GuildRankChangeResult {
    }

    record AlreadyLowestRank(@NonNull String targetName) implements GuildRankChangeResult {
    }

    record GuildNotFound() implements GuildRankChangeResult {
    }
}
