package com.zornus.punishments.proxy.model.result;

import com.zornus.punishments.proxy.model.Punishment;
import org.jspecify.annotations.NonNull;

public sealed interface PunishmentCheckResult {
    record Found(@NonNull Punishment punishment) implements PunishmentCheckResult {}
    record PlayerNotBanned() implements PunishmentCheckResult {}
    record PlayerNotMuted() implements PunishmentCheckResult {}
}
