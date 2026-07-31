package net.valoury.friends.proxy.storage;

public sealed interface SendRequestOutcome permits
        SendRequestOutcome.Sent,
        SendRequestOutcome.IncomingRequestExists,
        SendRequestOutcome.AlreadyFriends,
        SendRequestOutcome.RequestAlreadySent,
        SendRequestOutcome.SenderRequestLimitReached,
        SendRequestOutcome.ReceiverRequestLimitReached,
        SendRequestOutcome.SenderFriendsLimitReached,
        SendRequestOutcome.ReceiverFriendsLimitReached,
        SendRequestOutcome.RequestCooldownActive,
        SendRequestOutcome.PlayerNotAcceptingRequests {
    record Sent() implements SendRequestOutcome {
    }

    record IncomingRequestExists() implements SendRequestOutcome {
    }

    record AlreadyFriends() implements SendRequestOutcome {
    }

    record RequestAlreadySent() implements SendRequestOutcome {
    }

    record SenderRequestLimitReached() implements SendRequestOutcome {
    }

    record ReceiverRequestLimitReached() implements SendRequestOutcome {
    }

    record SenderFriendsLimitReached() implements SendRequestOutcome {
    }

    record ReceiverFriendsLimitReached() implements SendRequestOutcome {
    }

    record RequestCooldownActive() implements SendRequestOutcome {
    }

    record PlayerNotAcceptingRequests() implements SendRequestOutcome {
    }
}
