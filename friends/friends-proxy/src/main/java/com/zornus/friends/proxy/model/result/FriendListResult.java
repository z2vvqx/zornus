package com.zornus.friends.proxy.model.result;

import com.zornus.friends.proxy.model.FriendRelation;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

public record FriendListResult(
        @NonNull FriendResult result,
        @NonNull PaginationResult<FriendRelation> paginationResult
) {
}
