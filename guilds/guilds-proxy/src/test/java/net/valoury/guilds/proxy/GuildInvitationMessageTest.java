package net.valoury.guilds.proxy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.valoury.shared.utilities.SocialRequestActions;
import net.valoury.shared.utilities.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildInvitationMessageTest {

    @Test
    void invitationNotificationOffersClickableAcceptAndRejectActions() {
        Component notification = StringUtils.deserialize(
                GuildProxyConstants.NOTIFICATION_INVITE_RECEIVED,
                TagResolver.resolver(actions(),
                        Placeholder.unparsed("player", "Inviter"),
                        Placeholder.unparsed("guild", "Valoury")
                )
        );
        Component incomingInvitation = StringUtils.deserialize(
                GuildProxyConstants.UI_REQUESTS_INCOMING_ENTRY,
                TagResolver.resolver(
                        actions(),
                        Placeholder.unparsed("guild_name", "Valoury"),
                        Placeholder.unparsed("timestamp", "now")
                )
        );
        Component outgoingInvitation = StringUtils.deserialize(
                GuildProxyConstants.UI_REQUESTS_OUTGOING_ENTRY,
                TagResolver.resolver(
                        Placeholder.unparsed("player", "PlayerOne"),
                        Placeholder.unparsed("timestamp", "now"),
                        Placeholder.component(
                                "crossmark_action",
                                SocialRequestActions.crossmarkAction("/guild revoke PlayerOne")
                        )
                )
        );

        assertActions(notification);
        assertActions(incomingInvitation);
        assertCrossmarkAction(outgoingInvitation, "/guild revoke PlayerOne");
    }

    private static TagResolver actions() {
        return TagResolver.resolver(
                Placeholder.component(
                        "checkmark_action",
                        SocialRequestActions.checkmarkAction("/guild accept Valoury")
                ),
                Placeholder.component(
                        "crossmark_action",
                        SocialRequestActions.crossmarkAction("/guild reject Valoury")
                )
        );
    }

    private static void assertActions(Component message) {
        String plainMessage = plainText(message);
        assertTrue(plainMessage.contains("✔"));
        assertTrue(plainMessage.contains("✘"));

        List<ClickEvent> clickEvents = new ArrayList<>();
        collectClickEvents(message, clickEvents);
        assertEquals(
                List.of(
                        ClickEvent.runCommand("/guild accept Valoury"),
                        ClickEvent.runCommand("/guild reject Valoury")
                ),
                clickEvents
        );
    }

    private static void assertCrossmarkAction(Component message, String command) {
        assertTrue(plainText(message).contains("✘"));

        List<ClickEvent> clickEvents = new ArrayList<>();
        collectClickEvents(message, clickEvents);
        assertEquals(List.of(ClickEvent.runCommand(command)), clickEvents);
    }

    private static void collectClickEvents(Component component, List<ClickEvent> clickEvents) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null) {
            clickEvents.add(clickEvent);
        }
        component.children().forEach(child -> collectClickEvents(child, clickEvents));
    }

    private static String plainText(Component component) {
        StringBuilder plainText = new StringBuilder();
        appendText(component, plainText);
        return plainText.toString();
    }

    private static void appendText(Component component, StringBuilder plainText) {
        if (component instanceof TextComponent textComponent) {
            plainText.append(textComponent.content());
        }
        component.children().forEach(child -> appendText(child, plainText));
    }
}
