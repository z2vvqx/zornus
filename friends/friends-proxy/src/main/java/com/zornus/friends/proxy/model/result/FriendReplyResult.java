package com.zornus.friends.proxy.model.result;

import org.jspecify.annotations.NonNull;

public sealed interface FriendReplyResult {
    record Success() implements FriendReplyResult {}
    record MessageTooLong() implements FriendReplyResult {}
    record NoRecentMessage() implements FriendReplyResult {}
    record NotFriends(@NonNull String targetName) implements FriendReplyResult {}
    record FriendNotOnline(@NonNull String targetName) implements FriendReplyResult {}
    record PlayerNotAcceptingMessages(@NonNull String targetName) implements FriendReplyResult {}
    record ErrorAlreadyHandled() implements FriendReplyResult {}
}
