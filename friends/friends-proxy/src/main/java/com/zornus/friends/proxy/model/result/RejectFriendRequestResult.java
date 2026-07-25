package com.zornus.friends.proxy.model.result;

public sealed interface RejectFriendRequestResult {
    record Rejected() implements RejectFriendRequestResult {}
    record NoRequestFound() implements RejectFriendRequestResult {}
}
