package net.valoury.parties.proxy.storage;

public sealed interface TransferLeadershipOutcome permits
        TransferLeadershipOutcome.Transferred,
        TransferLeadershipOutcome.PartyNotFound,
        TransferLeadershipOutcome.NotLeader,
        TransferLeadershipOutcome.TargetNotMember {
    record Transferred() implements TransferLeadershipOutcome {
    }

    record PartyNotFound() implements TransferLeadershipOutcome {
    }

    record NotLeader() implements TransferLeadershipOutcome {
    }

    record TargetNotMember() implements TransferLeadershipOutcome {
    }
}
