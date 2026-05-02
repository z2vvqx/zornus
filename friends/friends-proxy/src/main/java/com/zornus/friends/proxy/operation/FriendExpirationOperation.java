package com.zornus.friends.proxy.operation;

import com.zornus.friends.proxy.service.FriendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FriendExpirationOperation implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendExpirationOperation.class);
    private final FriendService service;

    public FriendExpirationOperation(FriendService service) {
        this.service = service;
    }

    @Override
    public void run() {
        try {
            service.cleanupExpiredRequests();
            service.cleanupExpiredCooldowns();
            service.cleanupExpiredLastMessageSenders();
        } catch (Exception exception) {
            LOGGER.error("Error during friend cleanup", exception);
        }
    }
}
