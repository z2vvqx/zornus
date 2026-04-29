package com.zornus.friends.proxy.model.result;

import com.zornus.friends.proxy.model.FriendRequest;
import com.zornus.shared.utilities.PaginationResult;
import org.jetbrains.annotations.NotNull;

public record FriendRequestListResult(
        @NotNull FriendResult result,
        @NotNull PaginationResult<FriendRequest> paginationResult
) {
}
