package com.zornus.friends.proxy.model.result;

import com.zornus.friends.proxy.model.FriendRequest;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

public record FriendRequestListResult(
        @NonNull FriendResult result,
        @NonNull PaginationResult<FriendRequest> paginationResult
) {
}
