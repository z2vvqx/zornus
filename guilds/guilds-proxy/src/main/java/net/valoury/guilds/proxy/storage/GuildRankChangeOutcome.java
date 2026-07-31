package net.valoury.guilds.proxy.storage;

import net.valoury.guilds.proxy.model.GuildRank;

public sealed interface GuildRankChangeOutcome {
    record Changed(GuildRank previousRank, GuildRank newRank) implements GuildRankChangeOutcome {
    }

    record GuildNotFound() implements GuildRankChangeOutcome {
    }

    record ActorNotMember() implements GuildRankChangeOutcome {
    }

    record MemberNotFound() implements GuildRankChangeOutcome {
    }

    record CannotChangeSelf() implements GuildRankChangeOutcome {
    }

    record InsufficientRank() implements GuildRankChangeOutcome {
    }

    record CannotManageRank() implements GuildRankChangeOutcome {
    }

    record PromotionWouldMatchActorRank() implements GuildRankChangeOutcome {
    }

    record AlreadyHighestRank() implements GuildRankChangeOutcome {
    }

    record AlreadyLowestRank() implements GuildRankChangeOutcome {
    }
}
