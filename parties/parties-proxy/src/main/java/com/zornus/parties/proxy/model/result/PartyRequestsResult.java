package com.zornus.parties.proxy.model.result;

import com.zornus.parties.proxy.model.PartyInvitation;
import com.zornus.parties.proxy.model.PartyResult;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

public record PartyRequestsResult(
        @NonNull PartyResult result,
        @NonNull PaginationResult<PartyInvitation> pagination
) {
}
