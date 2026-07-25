package net.valoury.friends.proxy.model.result;

public sealed interface RevokeFriendRequestResult {
    record Revoked() implements RevokeFriendRequestResult {
    }

    record NoRequestFound() implements RevokeFriendRequestResult {
    }
}
