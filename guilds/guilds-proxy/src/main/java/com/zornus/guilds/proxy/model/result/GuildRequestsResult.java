package com.zornus.guilds.proxy.model.result;

import com.zornus.guilds.proxy.model.GuildInvitation;
import com.zornus.guilds.proxy.model.GuildResult;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

public record GuildRequestsResult(
        @NonNull GuildResult result,
        @NonNull PaginationResult<GuildInvitation> pagination
) {}
