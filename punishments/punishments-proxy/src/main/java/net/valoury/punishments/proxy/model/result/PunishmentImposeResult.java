package net.valoury.punishments.proxy.model.result;

import net.valoury.punishments.proxy.model.Punishment;
import org.jspecify.annotations.NonNull;

public sealed interface PunishmentImposeResult {
    record Imposed(@NonNull Punishment punishment) implements PunishmentImposeResult {
    }

    record PlayerNotFound() implements PunishmentImposeResult {
    }

    record CannotPunishSelf() implements PunishmentImposeResult {
    }

    record InvalidDuration() implements PunishmentImposeResult {
    }

    record AlreadyBanned() implements PunishmentImposeResult {
    }

    record AlreadyMuted() implements PunishmentImposeResult {
    }

    record AlreadyWarnedForReason() implements PunishmentImposeResult {
    }

    record PresetNotFound() implements PunishmentImposeResult {
    }
}
