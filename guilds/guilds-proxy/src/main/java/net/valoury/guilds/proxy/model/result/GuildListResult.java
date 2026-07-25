package net.valoury.guilds.proxy.model.result;

import net.valoury.guilds.proxy.model.Guild;
import net.valoury.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public sealed interface GuildListResult {
    record Found(
            @NonNull PaginationResult<UUID> pagination,
            @NonNull Guild guild
    ) implements GuildListResult {
    }

    record Empty() implements GuildListResult {
    }

    record InvalidPage(@NonNull PaginationResult<UUID> pagination) implements GuildListResult {
    }

    record NotInGuild() implements GuildListResult {
    }
}
