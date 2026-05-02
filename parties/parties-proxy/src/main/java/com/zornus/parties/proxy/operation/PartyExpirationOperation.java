package com.zornus.parties.proxy.operation;

import com.zornus.parties.proxy.service.PartyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartyExpirationOperation implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyExpirationOperation.class);
    private final PartyService service;

    public PartyExpirationOperation(PartyService service) {
        this.service = service;
    }

    @Override
    public void run() {
        try {
            service.cleanupExpiredInvitations();
            service.cleanupExpiredConfirmations();
            service.cleanupExpiredCooldowns();
            service.cleanupOrphanedSettings();
        } catch (Exception exception) {
            LOGGER.error("Error during party cleanup", exception);
        }
    }
}
