package com.zornus.friends.proxy.operation;

import com.zornus.friends.proxy.FriendProxyConstants;
import com.zornus.friends.proxy.storage.FriendStorage;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles periodic cleanup of expired friend requests and cooldowns.
 */
public class FriendExpirationOperation implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendExpirationOperation.class);

    private final @NonNull FriendStorage storage;

    /**
     * Creates a new friend request expiry operation.
     *
     * @param storage Storage for friend data
     */
    public FriendExpirationOperation(@NonNull FriendStorage storage) {
        this.storage = storage;
    }

    /**
     * Executes the expiry task.
     * Removes expired friend requests and cooldowns.
     */
    @Override
    public void run() {
        try {
            LOGGER.debug("Starting cleanup of expired friend requests, cooldowns, and last message senders...");

            Instant now = Instant.now();
            storage.cleanupExpiredFriendRequests(now, FriendProxyConstants.REQUEST_EXPIRY_DURATION);
            storage.cleanupExpiredFriendRequestCooldowns(now, FriendProxyConstants.COOLDOWN_EXPIRY_DURATION);
            storage.cleanupExpiredLastMessageSenders(now, FriendProxyConstants.LAST_MESSAGE_SENDER_RETENTION);

            LOGGER.debug("Completed cleanup of expired friend requests, cooldowns, and last message senders");
        } catch (Exception exception) {
            LOGGER.error("Error during friend request expiry operation: {}", exception.getMessage(), exception);
        }
    }
}
