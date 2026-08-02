package net.valoury.parties.proxy.storage;

public sealed interface PartyModeratorChangeOutcome {
    record Changed() implements PartyModeratorChangeOutcome {
    }

    record PartyNotFound() implements PartyModeratorChangeOutcome {
    }

    record NotLeader() implements PartyModeratorChangeOutcome {
    }

    record MemberNotFound() implements PartyModeratorChangeOutcome {
    }

    record CannotChangeLeader() implements PartyModeratorChangeOutcome {
    }

    record AlreadyModerator() implements PartyModeratorChangeOutcome {
    }

    record NotModerator() implements PartyModeratorChangeOutcome {
    }
}
