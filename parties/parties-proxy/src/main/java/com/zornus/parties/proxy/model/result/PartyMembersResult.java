package com.zornus.parties.proxy.model.result;

import com.zornus.parties.proxy.model.PartyResult;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record PartyMembersResult(
        @NonNull PartyResult result,
        @NonNull PaginationResult<UUID> pagination
) {
}
