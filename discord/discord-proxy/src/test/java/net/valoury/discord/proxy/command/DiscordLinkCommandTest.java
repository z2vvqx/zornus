package net.valoury.discord.proxy.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordLinkCommandTest {
    @Test
    void issuedLinkCodeCanBeClickedIntoChat() {
        Component message = DiscordLinkCommand.createIssuedLinkCodeMessage("ABCD-EFGH-JKLM");

        assertEquals(
                "Click /link ABCD-EFGH-JKLM to paste it into chat, "
                        + "then copy and send it in Discord within five minutes.",
                plainText(message)
        );

        List<ClickEvent> clickEvents = new ArrayList<>();
        collectClickEvents(message, clickEvents);
        assertEquals(
                List.of(ClickEvent.suggestCommand("/link ABCD-EFGH-JKLM")),
                clickEvents
        );
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
