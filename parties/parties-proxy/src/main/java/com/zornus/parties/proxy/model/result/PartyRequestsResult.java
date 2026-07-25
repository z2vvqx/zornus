package com.zornus.parties.proxy.model.result;

import com.zornus.parties.proxy.model.PartyInvitation;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

public sealed interface PartyRequestsResult {
    record Found(@NonNull PaginationResult<PartyInvitation> pagination) implements PartyRequestsResult {}
    record Empty() implements PartyRequestsResult {}
    record InvalidPage(@NonNull PaginationResult<PartyInvitation> pagination) implements PartyRequestsResult {}
    record InvalidRequestType() implements PartyRequestsResult {}
}
