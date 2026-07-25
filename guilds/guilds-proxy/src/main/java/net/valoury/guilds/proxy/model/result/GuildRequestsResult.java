package net.valoury.guilds.proxy.model.result;

import net.valoury.guilds.proxy.model.GuildInvitation;
import net.valoury.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

public sealed interface GuildRequestsResult {
    record Found(@NonNull PaginationResult<GuildInvitation> pagination) implements GuildRequestsResult {
    }

    record Empty() implements GuildRequestsResult {
    }

    record InvalidPage(@NonNull PaginationResult<GuildInvitation> pagination) implements GuildRequestsResult {
    }

    record InvalidRequestType() implements GuildRequestsResult {
    }
}
