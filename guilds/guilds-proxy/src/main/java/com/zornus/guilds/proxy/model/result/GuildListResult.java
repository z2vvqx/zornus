package com.zornus.guilds.proxy.model.result;

import com.zornus.guilds.proxy.model.Guild;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public sealed interface GuildListResult {
    record Found(
            @NonNull PaginationResult<UUID> pagination,
            @NonNull Guild guild
    ) implements GuildListResult {}

    record Empty() implements GuildListResult {}
    record InvalidPage(@NonNull PaginationResult<UUID> pagination) implements GuildListResult {}
    record NotInGuild() implements GuildListResult {}
}
