package com.zornus.friends.proxy.model.result;

import com.zornus.friends.proxy.model.FriendRelation;
import com.zornus.shared.utilities.PaginationResult;
import org.jetbrains.annotations.NotNull;

public record FriendListResult(
        @NotNull FriendResult result,
        @NotNull PaginationResult<FriendRelation> paginationResult
) {
}
