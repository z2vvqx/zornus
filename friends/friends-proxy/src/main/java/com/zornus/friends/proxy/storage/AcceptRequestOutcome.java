package com.zornus.friends.proxy.storage;

public sealed interface AcceptRequestOutcome permits
        AcceptRequestOutcome.Accepted,
        AcceptRequestOutcome.NoRequestFound,
        AcceptRequestOutcome.AlreadyFriends,
        AcceptRequestOutcome.AccepterFriendsLimitReached,
        AcceptRequestOutcome.RequesterFriendsLimitReached {
    record Accepted() implements AcceptRequestOutcome {}
    record NoRequestFound() implements AcceptRequestOutcome {}
    record AlreadyFriends() implements AcceptRequestOutcome {}
    record AccepterFriendsLimitReached() implements AcceptRequestOutcome {}
    record RequesterFriendsLimitReached() implements AcceptRequestOutcome {}
}
