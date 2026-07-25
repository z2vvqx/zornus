package net.valoury.friends.proxy.model.result;

import net.valoury.friends.proxy.model.FriendRequest;
import net.valoury.shared.utilities.PaginationResult;
import org.jspecify.annotations.NonNull;

public sealed interface FriendRequestListResult {
    record Found(@NonNull PaginationResult<FriendRequest> pagination) implements FriendRequestListResult {
    }

    record Empty() implements FriendRequestListResult {
    }

    record InvalidPage(@NonNull PaginationResult<FriendRequest> pagination) implements FriendRequestListResult {
    }
}
