package net.valoury.friends.proxy.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendSettingsTest {

    @Test
    void showsLocationByDefault() {
        FriendSettings settings = new FriendSettings(new UUID(0L, 0L));

        assertTrue(settings.showLocation());
    }
}
