package com.zornus.friends.proxy.model.result;

import com.zornus.friends.proxy.model.FriendRelation;
import com.zornus.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

public sealed interface FriendListResult {
    record Found(@NonNull PaginationResult<FriendRelation> pagination) implements FriendListResult {}
    record Empty() implements FriendListResult {}
    record InvalidPage(@NonNull PaginationResult<FriendRelation> pagination) implements FriendListResult {}
}
