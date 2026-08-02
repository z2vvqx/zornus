package net.valoury.parties.proxy;

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

class PartyInvitationMessageTest {

    @Test
    void receivedInvitationMessagesOfferClickableAcceptAndRejectActions() {
        Component notification = StringUtils.deserialize(
                PartyProxyConstants.NOTIFICATION_INVITE_RECEIVED,
                resolver("PlayerOne")
        );
        Component requestEntry = StringUtils.deserialize(
                PartyProxyConstants.UI_REQUESTS_INCOMING_ENTRY,
                TagResolver.resolver(
                        resolver("PlayerOne"),
                        Placeholder.unparsed("player", "PlayerOne"),
                        Placeholder.unparsed("timestamp", "now")
                )
        );
        Component outgoingInvitation = StringUtils.deserialize(
                PartyProxyConstants.UI_REQUESTS_OUTGOING_ENTRY,
                TagResolver.resolver(
                        Placeholder.unparsed("player", "PlayerOne"),
                        Placeholder.unparsed("timestamp", "now"),
                        Placeholder.component(
                                "crossmark_action",
                                SocialRequestActions.crossmarkAction("/party uninvite PlayerOne")
                        )
                )
        );

        assertActions(notification);
        assertActions(requestEntry);
        assertCrossmarkAction(outgoingInvitation, "/party uninvite PlayerOne");
    }

    private static TagResolver resolver(String playerName) {
        return TagResolver.resolver(
                Placeholder.unparsed("player", playerName),
                Placeholder.component(
                        "checkmark_action",
                        SocialRequestActions.checkmarkAction("/party accept " + playerName)
                ),
                Placeholder.component(
                        "crossmark_action",
                        SocialRequestActions.crossmarkAction("/party reject " + playerName)
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
                        ClickEvent.runCommand("/party accept PlayerOne"),
                        ClickEvent.runCommand("/party reject PlayerOne")
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
