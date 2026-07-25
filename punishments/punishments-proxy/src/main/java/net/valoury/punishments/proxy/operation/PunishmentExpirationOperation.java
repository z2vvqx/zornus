package net.valoury.punishments.proxy.operation;

import net.valoury.punishments.proxy.service.PunishmentService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PunishmentExpirationOperation implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentExpirationOperation.class);

    private final @NonNull PunishmentService punishmentService;

    public PunishmentExpirationOperation(@NonNull PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    @Override
    public void run() {
        punishmentService.expirePunishments()
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to expire punishments", throwable);
                    return null;
                });
    }
}
