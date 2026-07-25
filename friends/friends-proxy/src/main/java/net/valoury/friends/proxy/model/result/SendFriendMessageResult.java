package net.valoury.friends.proxy.model.result;

public sealed interface SendFriendMessageResult {
    record Sent() implements SendFriendMessageResult {
    }

    record NotFriends() implements SendFriendMessageResult {
    }

    record ReceiverNotAcceptingMessages() implements SendFriendMessageResult {
    }

    record FriendNotOnline() implements SendFriendMessageResult {
    }

    record MessageTooLong() implements SendFriendMessageResult {
    }
}
