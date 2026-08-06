package net.valoury.staff.proxy.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.valoury.shared.model.PlayerRecord;
import net.valoury.staff.proxy.StaffProxyConstants;
import net.valoury.staff.proxy.model.AddressFingerprint;
import net.valoury.staff.proxy.model.ConnectionSummary;
import net.valoury.staff.proxy.model.RelatedAccount;
import net.valoury.staff.proxy.model.StaffInspection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffCommandDisplayTest {
    private static final UUID TARGET_PLAYER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RELATED_PLAYER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final AddressFingerprint ADDRESS_FINGERPRINT =
            new AddressFingerprint("0".repeat(52));
    private static final Instant FIRST_SEEN_AT = Instant.now().minus(Duration.ofDays(10));
    private static final Instant LAST_SEEN_AT = Instant.now().minus(Duration.ofHours(1));

    @Test
    void inspectDisplaysACompactUnpaginatedOverview() {
        StaffInspection inspection = inspection();
        Component display = StaffInspectCommand.createDisplay(
                inspection,
                offlineProxyServer()
        );
        String plainText = plainText(display);

        assertTrue(plainText.startsWith("\n"));
        assertTrue(plainText.endsWith("\n"));
        assertTrue(plainText.contains("Player ─ Target"));
        assertTrue(plainText.contains("Status ─ Offline"));
        assertTrue(plainText.contains("Window ─ 30 days"));
        assertTrue(plainText.contains("Connection Events ─ 4"));
        assertTrue(plainText.contains("Address IDs ─ 1"));
        assertTrue(plainText.contains(
                "Latest IP ─ " + ADDRESS_FINGERPRINT.displayIdentifier()
        ));
        assertFalse(plainText.contains(ADDRESS_FINGERPRINT.encodedValue()));
        assertTrue(plainText.contains("Related Accounts ─ 1"));
        assertFalse(plainText.contains("Page "));
    }

    @Test
    void connectionsUseTwoLinesWithoutAHeaderAndKeepTheIdentifierShort() {
        List<Component> messages = new ArrayList<>();
        StaffConnectionsCommand.sendConnections(
                capturingSource(messages),
                inspection(),
                1
        );

        assertEquals(1, messages.size());
        String plainText = plainText(messages.getFirst());
        String messageBody = plainText.substring(1, plainText.length() - 1);
        assertTrue(plainText.startsWith("\n"));
        assertTrue(plainText.endsWith("\n"));
        assertEquals(2, messageBody.lines().count());
        String timespanLine = messageBody.lines().toList().getLast();
        assertTrue(timespanLine.startsWith("   "));
        assertTrue(timespanLine.contains(" ─ "));
        assertFalse(timespanLine.contains(":"));
        assertFalse(plainText.contains("Connections for Target"));
        assertTrue(plainText.contains(ADDRESS_FINGERPRINT.displayIdentifier()));
        assertFalse(plainText.contains(ADDRESS_FINGERPRINT.encodedValue()));
        assertFalse(plainText.contains("F:"));
        assertFalse(plainText.contains("L:"));
        assertFalse(plainText.contains("First seen:"));
        assertFalse(plainText.contains("Last seen:"));
    }

    @Test
    void connectionAccountCountSuggestsTheRelatedCommand() {
        List<Component> messages = new ArrayList<>();
        StaffConnectionsCommand.sendConnections(
                capturingSource(messages),
                inspection(),
                1
        );

        List<Component> clickableComponents = componentsWithClickEvents(
                messages.getFirst()
        );
        assertEquals(1, clickableComponents.size());
        Component accountCount = clickableComponents.getFirst();
        assertEquals("2 account(s)", plainText(accountCount));
        assertSuggestedCommand(
                accountCount,
                "/staff related Target " + ADDRESS_FINGERPRINT.displayIdentifier()
        );
    }

    @Test
    void filteredRelatedDisplaysOnlyAccountsSharingTheSelectedConnection() {
        AddressFingerprint secondAddressFingerprint =
                new AddressFingerprint("1".repeat(52));
        StaffInspection inspection = new StaffInspection(
                new PlayerRecord(TARGET_PLAYER_UUID, "Target"),
                Optional.of(FIRST_SEEN_AT),
                Optional.of(LAST_SEEN_AT),
                2,
                List.of(
                        new ConnectionSummary(
                                ADDRESS_FINGERPRINT,
                                FIRST_SEEN_AT,
                                LAST_SEEN_AT,
                                1,
                                2
                        ),
                        new ConnectionSummary(
                                secondAddressFingerprint,
                                FIRST_SEEN_AT,
                                LAST_SEEN_AT,
                                1,
                                2
                        )
                ),
                List.of(
                        relatedAccount("Selected", ADDRESS_FINGERPRINT),
                        relatedAccount("Other", secondAddressFingerprint),
                        new RelatedAccount(
                                new UUID(0, 4),
                                "Indirect",
                                2,
                                Set.of(),
                                "Selected",
                                FIRST_SEEN_AT,
                                LAST_SEEN_AT
                        )
                )
        );
        List<Component> messages = new ArrayList<>();

        StaffRelatedCommand.sendRelatedAccounts(
                capturingSource(messages),
                inspection,
                ADDRESS_FINGERPRINT.displayIdentifier(),
                1
        );

        String plainText = plainText(messages.getFirst());
        assertTrue(plainText.contains("Selected"));
        assertFalse(plainText.contains("Other"));
        assertFalse(plainText.contains("Indirect"));
    }

    @Test
    void filteredRelatedRejectsUnknownAndAmbiguousShortIdentifiers() {
        List<Component> unknownMessages = new ArrayList<>();
        StaffRelatedCommand.sendRelatedAccounts(
                capturingSource(unknownMessages),
                inspection(),
                "IP-1111-1111-1111",
                1
        );
        assertTrue(plainText(unknownMessages.getFirst()).contains(
                "not retained for this player"
        ));

        AddressFingerprint collidingFingerprint = new AddressFingerprint(
                "0".repeat(12) + "1".repeat(40)
        );
        StaffInspection ambiguousInspection = new StaffInspection(
                new PlayerRecord(TARGET_PLAYER_UUID, "Target"),
                Optional.of(FIRST_SEEN_AT),
                Optional.of(LAST_SEEN_AT),
                2,
                List.of(
                        new ConnectionSummary(
                                ADDRESS_FINGERPRINT,
                                FIRST_SEEN_AT,
                                LAST_SEEN_AT,
                                1,
                                1
                        ),
                        new ConnectionSummary(
                                collidingFingerprint,
                                FIRST_SEEN_AT,
                                LAST_SEEN_AT,
                                1,
                                1
                        )
                ),
                List.of()
        );
        List<Component> ambiguousMessages = new ArrayList<>();
        StaffRelatedCommand.sendRelatedAccounts(
                capturingSource(ambiguousMessages),
                ambiguousInspection,
                ADDRESS_FINGERPRINT.displayIdentifier(),
                1
        );
        assertTrue(plainText(ambiguousMessages.getFirst()).contains("ambiguous"));
    }

    @Test
    void relatedAccountNameAloneSuggestsTheInspectCommand() {
        List<Component> messages = new ArrayList<>();
        StaffRelatedCommand.sendRelatedAccounts(
                capturingSource(messages),
                inspection(),
                1
        );

        assertEquals(1, messages.size());
        String plainText = plainText(messages.getFirst());
        String messageBody = plainText.substring(1, plainText.length() - 1);
        assertEquals(2, messageBody.lines().count());
        String timespanLine = messageBody.lines().toList().getLast();
        assertTrue(timespanLine.startsWith("   "));
        assertTrue(timespanLine.contains(" ─ "));
        assertFalse(timespanLine.contains(":"));
        assertFalse(plainText.contains("Related accounts for Target"));
        assertFalse(plainText.contains("F:"));
        assertFalse(plainText.contains("L:"));
        List<Component> clickableComponents = componentsWithClickEvents(
                messages.getFirst()
        );
        assertEquals(1, clickableComponents.size());
        Component relatedPlayerName = clickableComponents.getFirst();
        assertEquals("Related", plainText(relatedPlayerName));
        assertSuggestedCommand(relatedPlayerName, "/staff inspect Related");
    }

    private static void assertSuggestedCommand(Component component, String expectedCommand) {
        ClickEvent clickEvent = component.clickEvent();
        assertNotNull(clickEvent);
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, clickEvent.action());
        ClickEvent.Payload.Text command = assertInstanceOf(
                ClickEvent.Payload.Text.class,
                clickEvent.payload()
        );
        assertEquals(expectedCommand, command.value());
    }

    @Test
    void connectionsUseTheOptionalPageArgumentForLongResults() {
        String fingerprintCharacters = "0123456789ABCDEFG";
        List<ConnectionSummary> connections = IntStream.range(0, 17)
                .mapToObj(index -> new ConnectionSummary(
                        new AddressFingerprint(
                                String.valueOf(fingerprintCharacters.charAt(index)).repeat(52)
                        ),
                        FIRST_SEEN_AT,
                        LAST_SEEN_AT,
                        index + 1,
                        1
                ))
                .toList();
        StaffInspection inspection = new StaffInspection(
                new PlayerRecord(TARGET_PLAYER_UUID, "Target"),
                Optional.of(FIRST_SEEN_AT),
                Optional.of(LAST_SEEN_AT),
                153,
                connections,
                List.of()
        );
        List<Component> messages = new ArrayList<>();

        StaffConnectionsCommand.sendConnections(
                capturingSource(messages),
                inspection,
                2
        );

        String plainText = plainText(messages.getFirst());
        assertTrue(plainText.contains(connections.getLast()
                .addressFingerprint()
                .displayIdentifier()));
        assertTrue(plainText.contains("Page 2/2"));
        assertFalse(plainText.contains(connections.getFirst()
                .addressFingerprint()
                .displayIdentifier()));
    }

    @Test
    void relatedAccountsUseTheOptionalPageArgumentForLongResults() {
        List<RelatedAccount> relatedAccounts = IntStream.range(0, 17)
                .mapToObj(index -> new RelatedAccount(
                        new UUID(0, index + 2L),
                        "Related" + index,
                        1,
                        Set.of(ADDRESS_FINGERPRINT),
                        null,
                        FIRST_SEEN_AT,
                        LAST_SEEN_AT.minusSeconds(index)
                ))
                .toList();
        StaffInspection inspection = new StaffInspection(
                new PlayerRecord(TARGET_PLAYER_UUID, "Target"),
                Optional.of(FIRST_SEEN_AT),
                Optional.of(LAST_SEEN_AT),
                1,
                List.of(),
                relatedAccounts
        );
        List<Component> messages = new ArrayList<>();

        StaffRelatedCommand.sendRelatedAccounts(
                capturingSource(messages),
                inspection,
                2
        );

        String plainText = plainText(messages.getFirst());
        assertTrue(plainText.contains("Related16"));
        assertTrue(plainText.contains("Page 2/2"));
        assertFalse(plainText.contains("Related0 "));
    }

    @Test
    void retentionWindowRemainsExactlyThirtyDays() {
        assertEquals(Duration.ofDays(30), StaffProxyConstants.CONNECTION_RETENTION);
        assertEquals(30, StaffProxyConstants.CONNECTION_RETENTION_DAYS);
    }

    private static StaffInspection inspection() {
        return new StaffInspection(
                new PlayerRecord(TARGET_PLAYER_UUID, "Target"),
                Optional.of(FIRST_SEEN_AT),
                Optional.of(LAST_SEEN_AT),
                4,
                List.of(new ConnectionSummary(
                        ADDRESS_FINGERPRINT,
                        FIRST_SEEN_AT,
                        LAST_SEEN_AT,
                        4,
                        2
                )),
                List.of(new RelatedAccount(
                        RELATED_PLAYER_UUID,
                        "Related",
                        1,
                        Set.of(ADDRESS_FINGERPRINT),
                        null,
                        FIRST_SEEN_AT,
                        LAST_SEEN_AT
                ))
        );
    }

    private static RelatedAccount relatedAccount(
            String username,
            AddressFingerprint addressFingerprint
    ) {
        return new RelatedAccount(
                UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)),
                username,
                1,
                Set.of(addressFingerprint),
                null,
                FIRST_SEEN_AT,
                LAST_SEEN_AT
        );
    }

    private static ProxyServer offlineProxyServer() {
        return (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (instance, method, arguments) -> {
                    if (method.getName().equals("getPlayer")) {
                        return Optional.empty();
                    }
                    throw new AssertionError("Unexpected proxy call: " + method.getName());
                }
        );
    }

    private static CommandSource capturingSource(List<Component> messages) {
        return (CommandSource) Proxy.newProxyInstance(
                CommandSource.class.getClassLoader(),
                new Class<?>[]{CommandSource.class},
                (instance, method, arguments) -> {
                    if (method.getName().equals("sendMessage")) {
                        for (Object argument : arguments) {
                            if (argument instanceof Component component) {
                                messages.add(component);
                            }
                        }
                        return null;
                    }
                    throw new AssertionError("Unexpected command source call: " + method.getName());
                }
        );
    }

    private static String plainText(Component component) {
        StringBuilder text = new StringBuilder();
        appendPlainText(component, text);
        return text.toString();
    }

    private static void appendPlainText(Component component, StringBuilder text) {
        if (component instanceof TextComponent textComponent) {
            text.append(textComponent.content());
        }
        for (Component child : component.children()) {
            appendPlainText(child, text);
        }
    }

    private static List<Component> componentsWithClickEvents(Component component) {
        List<Component> clickableComponents = new ArrayList<>();
        collectComponentsWithClickEvents(component, clickableComponents);
        return List.copyOf(clickableComponents);
    }

    private static void collectComponentsWithClickEvents(
            Component component,
            List<Component> clickableComponents
    ) {
        if (component.clickEvent() != null) {
            clickableComponents.add(component);
        }
        for (Component child : component.children()) {
            collectComponentsWithClickEvents(child, clickableComponents);
        }
    }
}
