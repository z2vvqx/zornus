package com.zornus.guilds.proxy.model.result;

import com.zornus.guilds.proxy.model.GuildResult;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record GuildListResult(
        @NonNull GuildResult result,
        @NonNull PaginationResult<UUID> pagination
) {}
