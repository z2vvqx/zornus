package com.zornus.friends.proxy.model.result;

public sealed interface JumpToFriendResult {
    record Jumped() implements JumpToFriendResult {}
    record NotFriends() implements JumpToFriendResult {}
    record TargetNotAllowingJump() implements JumpToFriendResult {}
    record FriendNotOnline() implements JumpToFriendResult {}
    record PlayerNotOnline() implements JumpToFriendResult {}
    record FriendHasNoInstance() implements JumpToFriendResult {}
    record AlreadyInSameInstance() implements JumpToFriendResult {}
    record JumpFailed() implements JumpToFriendResult {}
}
