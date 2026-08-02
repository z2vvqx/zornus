package net.valoury.friends.proxy.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.valoury.friends.proxy.model.FriendSettings;
import net.valoury.friends.proxy.model.PresenceState;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FriendLocationDisplayTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void displaysCurrentServerAndSuggestsJumpingToFriend() {
        Component entry = FriendListEntryRenderer.render(
                "PlayerOne",
                settings(true),
                Optional.empty(),
                true,
                Optional.of("bloodstone")
        );

        assertEquals(
                " ► ▲ PlayerOne ─ Playing on bloodstone",
                plainText(entry)
        );
        assertEquals(
                ClickEvent.suggestCommand("/friend jump PlayerOne"),
                findTextComponent(entry, "bloodstone").orElseThrow().clickEvent()
        );
    }

    @Test
    void hidesCurrentServerWhenLocationSharingIsDisabled() {
        Component entry = FriendListEntryRenderer.render(
                "PlayerOne",
                settings(false),
                Optional.empty(),
                true,
                Optional.of("bloodstone")
        );

        assertEquals(" ► ▲ PlayerOne (Online)", plainText(entry));
    }

    @Test
    void hidesCurrentServerWhenPresenceIsOffline() {
        FriendSettings hiddenPresenceSettings = new FriendSettings(
                PLAYER_ID,
                PresenceState.OFFLINE,
                true,
                true,
                true,
                true,
                true
        );
        Component entry = FriendListEntryRenderer.render(
                "PlayerOne",
                hiddenPresenceSettings,
                Optional.empty(),
                true,
                Optional.of("bloodstone")
        );

        assertEquals(" ► ▼ PlayerOne (Offline)", plainText(entry));
    }

    @Test
    void doesNotCreateJumpActionForInvalidStoredUsername() {
        Component entry = FriendListEntryRenderer.render(
                "Player One",
                settings(true),
                Optional.empty(),
                true,
                Optional.of("bloodstone")
        );

        assertNull(findTextComponent(entry, "bloodstone").orElseThrow().clickEvent());
    }

    private static FriendSettings settings(boolean showLocation) {
        return new FriendSettings(
                PLAYER_ID,
                PresenceState.ONLINE,
                true,
                true,
                true,
                showLocation,
                true
        );
    }

    private static Optional<Component> findTextComponent(Component component, String content) {
        if (content.equals(component instanceof TextComponent textComponent ? textComponent.content() : null)) {
            return Optional.of(component);
        }
        return component.children().stream()
                .map(child -> findTextComponent(child, content))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static String plainText(Component component) {
        String content = component instanceof TextComponent textComponent ? textComponent.content() : "";
        return component.children().stream()
                .map(FriendLocationDisplayTest::plainText)
                .reduce(content, String::concat);
    }
}
