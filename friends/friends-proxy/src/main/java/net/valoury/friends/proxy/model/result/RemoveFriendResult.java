package net.valoury.friends.proxy.model.result;

public sealed interface RemoveFriendResult {
    record Removed() implements RemoveFriendResult {
    }

    record NotFriends() implements RemoveFriendResult {
    }
}
