package com.zornus.guilds.proxy.operation;

import com.zornus.guilds.proxy.service.GuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuildExpirationOperation implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildExpirationOperation.class);
    private final GuildService service;

    public GuildExpirationOperation(GuildService service) {
        this.service = service;
    }

    @Override
    public void run() {
        try {
            service.cleanupExpiredInvitations();
            service.cleanupExpiredConfirmations();
            service.cleanupExpiredCooldowns();
        } catch (Exception exception) {
            LOGGER.error("Error during guild cleanup", exception);
        }
    }
}
