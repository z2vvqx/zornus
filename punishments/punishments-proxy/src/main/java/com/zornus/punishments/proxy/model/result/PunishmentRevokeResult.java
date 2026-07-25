package com.zornus.punishments.proxy.model.result;

import com.zornus.punishments.proxy.model.Punishment;
import org.jspecify.annotations.NonNull;

public sealed interface PunishmentRevokeResult {
    record Revoked(@NonNull Punishment punishment) implements PunishmentRevokeResult {}
    record PunishmentNotFound() implements PunishmentRevokeResult {}
    record PlayerNotBanned() implements PunishmentRevokeResult {}
    record PlayerNotMuted() implements PunishmentRevokeResult {}
}
