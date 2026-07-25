package net.valoury.friends.proxy.model.result;

public sealed interface AcceptFriendRequestResult {
    record Accepted() implements AcceptFriendRequestResult {
    }

    record NoRequestFound() implements AcceptFriendRequestResult {
    }

    record AlreadyFriends() implements AcceptFriendRequestResult {
    }

    record AccepterFriendLimitReached() implements AcceptFriendRequestResult {
    }

    record RequesterFriendLimitReached() implements AcceptFriendRequestResult {
    }
}
