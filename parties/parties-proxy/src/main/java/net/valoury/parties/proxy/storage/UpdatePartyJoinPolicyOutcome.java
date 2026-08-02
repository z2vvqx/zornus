package net.valoury.parties.proxy.storage;

public sealed interface UpdatePartyJoinPolicyOutcome {
    record Updated() implements UpdatePartyJoinPolicyOutcome {
    }

    record NotLeader() implements UpdatePartyJoinPolicyOutcome {
    }

    record PartyNotFound() implements UpdatePartyJoinPolicyOutcome {
    }
}
