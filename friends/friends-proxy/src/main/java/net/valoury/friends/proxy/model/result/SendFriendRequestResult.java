package net.valoury.friends.proxy.model.result;

public sealed interface SendFriendRequestResult {
    record Sent() implements SendFriendRequestResult {
    }

    record AcceptedAutomatically() implements SendFriendRequestResult {
    }

    record CannotAddSelf() implements SendFriendRequestResult {
    }

    record AlreadyFriends() implements SendFriendRequestResult {
    }

    record AlreadySent() implements SendFriendRequestResult {
    }

    record SenderFriendLimitReached() implements SendFriendRequestResult {
    }

    record ReceiverFriendLimitReached() implements SendFriendRequestResult {
    }

    record SenderRequestLimitReached() implements SendFriendRequestResult {
    }

    record ReceiverRequestLimitReached() implements SendFriendRequestResult {
    }

    record CooldownActive() implements SendFriendRequestResult {
    }

    record ReceiverNotAcceptingRequests() implements SendFriendRequestResult {
    }

    record RequestNoLongerValid() implements SendFriendRequestResult {
    }
}
