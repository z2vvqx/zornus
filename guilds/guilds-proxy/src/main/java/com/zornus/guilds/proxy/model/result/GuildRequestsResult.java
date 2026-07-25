package com.zornus.guilds.proxy.model.result;

import com.zornus.guilds.proxy.model.GuildInvitation;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

public sealed interface GuildRequestsResult {
    record Found(@NonNull PaginationResult<GuildInvitation> pagination) implements GuildRequestsResult {}
    record Empty() implements GuildRequestsResult {}
    record InvalidPage(@NonNull PaginationResult<GuildInvitation> pagination) implements GuildRequestsResult {}
    record InvalidRequestType() implements GuildRequestsResult {}
}
