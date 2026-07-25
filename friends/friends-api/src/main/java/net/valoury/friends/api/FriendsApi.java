package net.valoury.friends.api;

import org.jspecify.annotations.NonNull;

/**
 * Public entry point exposed by the friends proxy plugin.
 */
public interface FriendsApi {

    String PLUGIN_ID = "friends-proxy";

    /**
     * Gets the read-only friendship service.
     *
     * @return friendship service
     */
    @NonNull FriendshipService friendships();
}
