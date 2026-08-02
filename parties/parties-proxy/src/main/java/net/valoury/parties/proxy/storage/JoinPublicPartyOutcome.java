package net.valoury.parties.proxy.storage;

public sealed interface JoinPublicPartyOutcome {
    record Joined() implements JoinPublicPartyOutcome {
    }

    record AlreadyInParty() implements JoinPublicPartyOutcome {
    }

    record PartyFull() implements JoinPublicPartyOutcome {
    }

    record PartyPrivate() implements JoinPublicPartyOutcome {
    }

    record PartyNotFound() implements JoinPublicPartyOutcome {
    }

    record TargetNotLeader() implements JoinPublicPartyOutcome {
    }
}
