package net.valoury.parties.proxy.storage;

public sealed interface WarpOutcome permits
        WarpOutcome.Allowed,
        WarpOutcome.OnCooldown,
        WarpOutcome.NotLeader,
        WarpOutcome.PartyNotFound {
    record Allowed() implements WarpOutcome {
    }

    record OnCooldown() implements WarpOutcome {
    }

    record NotLeader() implements WarpOutcome {
    }

    record PartyNotFound() implements WarpOutcome {
    }
}
