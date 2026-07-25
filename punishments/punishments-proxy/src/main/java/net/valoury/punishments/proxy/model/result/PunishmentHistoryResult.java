package net.valoury.punishments.proxy.model.result;

import net.valoury.punishments.proxy.model.Punishment;
import org.jspecify.annotations.NonNull;

import java.util.List;

public sealed interface PunishmentHistoryResult {
    record Found(@NonNull List<Punishment> punishments) implements PunishmentHistoryResult {
    }

    record Empty() implements PunishmentHistoryResult {
    }
}
