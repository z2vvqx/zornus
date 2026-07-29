package net.valoury.bloodstone.server.storage;

public sealed interface ExtraStorageUnlockOutcome permits
        ExtraStorageUnlockOutcome.Unlocked,
        ExtraStorageUnlockOutcome.AlreadyUnlocked,
        ExtraStorageUnlockOutcome.PlayerNotFound {

    record Unlocked() implements ExtraStorageUnlockOutcome {
    }

    record AlreadyUnlocked() implements ExtraStorageUnlockOutcome {
    }

    record PlayerNotFound() implements ExtraStorageUnlockOutcome {
    }
}
