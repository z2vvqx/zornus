package com.zornus.punishments.proxy.storage;

public sealed interface CreatePunishmentOutcome {
    record Created() implements CreatePunishmentOutcome {}
    record IdentifierCollision() implements CreatePunishmentOutcome {}
    record AlreadyActive() implements CreatePunishmentOutcome {}
    record PresetProgressionConflict() implements CreatePunishmentOutcome {}
}
